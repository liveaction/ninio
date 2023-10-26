package com.davfx.ninio.proxy;

import com.davfx.ninio.core.*;
import com.davfx.ninio.core.supervision.metrics.DisplayableMetricsManager;
import com.davfx.ninio.core.supervision.tracking.RequestTracker;
import com.davfx.ninio.core.supervision.tracking.RequestTrackerManager;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

public final class ProxyClient implements ProxyProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyClient.class);

    public static NinioBuilder<ProxyProvider> defaultSecureClient(final Address address,
                                                                  Optional<String> keyStorePath,
                                                                  Optional<String> keyStorePwd) {
        if (!keyStorePath.isPresent())
            throw new IllegalArgumentException("Key store path is mandatory when using secure option");
        if (!keyStorePwd.isPresent())
            throw new IllegalArgumentException("Key store password is mandatory when using secure option");
        return defaultClient(address, true, keyStorePath.get(), keyStorePwd.get());

    }

    public static NinioBuilder<ProxyProvider> defaultUnsecureClient(final Address address) {
        return defaultClient(address, false, null, null);
    }

    public static NinioBuilder<ProxyProvider> defaultClient(final Address address,
                                                            boolean secure,
                                                            String keyStorePath,
                                                            String keyStorePwd) {
        return ninioProvider -> {
            final ProxyClient client = secure ? ProxyClient.builder()
                    .with(new SecureSocketBuilder(TcpSocket.builder()).trust(new Trust(keyStorePath, keyStorePwd, keyStorePath, keyStorePwd)).to(address))
                    .create(ninioProvider) :
                    ProxyClient.builder().with(TcpSocket.builder().to(address)).create(ninioProvider);
            return new ProxyProvider() {
                @Override
                public void close() {
                    client.close();
                }

                @Override
                public WithHeaderSocketBuilder factory() {
                    return client.factory();
                }

                @Override
                public RawSocket.Builder raw() {
                    return client.raw();
                }
            };
        };
    }

    public interface Builder extends NinioBuilder<ProxyClient> {
        @Deprecated
        Builder with(Executor executor);

        Builder with(TcpSocket.Builder connectorFactory);
    }

    public static Builder builder() {
        return new Builder() {
            private TcpSocket.Builder connectorFactory = TcpSocket.builder();

            @Deprecated
            @Override
            public Builder with(Executor executor) {
                return this;
            }

            @Override
            public Builder with(TcpSocket.Builder connectorFactory) {
                this.connectorFactory = connectorFactory;
                return this;
            }

            @Override
            public ProxyClient create(NinioProvider ninioProvider) {
                return new ProxyClient(ninioProvider, connectorFactory);
            }
        };
    }

    private final Executor proxyExecutor;
    private final NinioProvider ninioProvider;
    private final TcpSocket.Builder proxyConnectorFactory;
    private Connecter proxyConnector = null;
    private int nextConnectionId = 0;

    private static final class InnerConnection {
        public int connectionId;
        public Connection connection = null;

        public InnerConnection() {
        }
    }

    private final Map<Integer, InnerConnection> connections = new HashMap<>();

    private ProxyClient(NinioProvider ninioProvider, TcpSocket.Builder proxyConnectorFactory) {
        proxyExecutor = ninioProvider.executor();
        this.ninioProvider = ninioProvider;
        this.proxyConnectorFactory = proxyConnectorFactory;
    }

    @Override
    public void close() {
        proxyExecutor.execute(() -> {
            if (proxyConnector != null) {
                proxyConnector.close();
            }
        });
    }

    @Override
    public WithHeaderSocketBuilder factory() {
        return new WithHeaderSocketBuilder() {
            private ProxyHeader header;
            private Address address;

            @Override
            public WithHeaderSocketBuilder header(ProxyHeader header) {
                this.header = header;
                return this;
            }

            @Override
            public WithHeaderSocketBuilder with(Address address) {
                this.address = address;
                return this;
            }

            @Override
            public Connecter create(NinioProvider ninioProvider) {
                if (header == null) {
                    throw new NullPointerException("header");
                }
                return createConnector(header, address);
            }
        };
    }

    @Override
    public RawSocket.Builder raw() {
        return new RawSocket.Builder() {
            private ProtocolFamily family = StandardProtocolFamily.INET;
            private int protocol = 0;

            @Override
            public RawSocket.Builder family(ProtocolFamily family) {
                this.family = family;
                return this;
            }

            @Override
            public RawSocket.Builder protocol(int protocol) {
                this.protocol = protocol;
                return this;
            }

            @Override
            public RawSocket.Builder bind(Address bindAddress) {
                return this;
            }

            @Override
            public Connecter create(NinioProvider ninioProvider) {
                return createConnector(new ProxyHeader(ProxyCommons.Types.RAW, ImmutableMap.of("family", (family == StandardProtocolFamily.INET6) ? "6" : "4", "protocol", String.valueOf(protocol))), null);
            }
        };
    }

    private Connecter createConnector(ProxyHeader header, Address connectAddress) {
        return new InnerConnector(header, connectAddress);
    }

    private final class InnerConnector implements Connecter {
        private final ProxyHeader header;
        private final Address connectAddress;
        private final InnerConnection innerConnection;
        private final RequestTracker inTracker;
        private final RequestTracker outTracker;

        public InnerConnector(ProxyHeader header, final Address connectAddress) {
            this.header = header;
            this.connectAddress = connectAddress;

            innerConnection = new InnerConnection();
            String prefix = "proxy-client";
            inTracker = RequestTrackerManager.instance().getTracker(prefix, "in");
            outTracker = RequestTrackerManager.instance().getTracker(prefix, "out");
            DisplayableMetricsManager.instance().percent(outTracker, inTracker, prefix, "lost");

            proxyExecutor.execute(() -> {
                innerConnection.connectionId = nextConnectionId;
                nextConnectionId++;

                connections.put(innerConnection.connectionId, innerConnection);
            });
        }

        private void doClose() {
            proxyExecutor.execute(() -> {
                if (proxyConnector != null) {
                    proxyConnector.close();
                    proxyConnector = null;
                }
                for (InnerConnection c : connections.values()) {
                    c.connection.failed(new IOException("Abruptly closed"));
                }
                connections.clear();
            });
        }

        @Override
        public void connect(final Connection callback) {
            proxyExecutor.execute(() -> {
                if (innerConnection.connection != null) {
                    throw new IllegalStateException("connect() cannot be called twice");
                }

                if (proxyConnector == null) {
                    proxyConnector = proxyConnectorFactory.create(ninioProvider);
                    proxyConnector.connect(new Connection() {
                        @Override
                        public void connected(Address address) {
                        }

                        @Override
                        public void closed() {
                            proxyExecutor.execute(() -> {
                                for (InnerConnection c : connections.values()) {
                                    c.connection.failed(new IOException("Abruptly closed"));
                                }
                                connections.clear();

                                proxyConnector = null;
                            });
                        }

                        @Override
                        public void failed(final IOException e) {
                            proxyExecutor.execute(() -> {
                                for (InnerConnection c : connections.values()) {
                                    c.connection.failed(e);
                                }
                                connections.clear();

                                proxyConnector = null;
                            });
                        }

                        private ByteBuffer readByteBuffer;

                        private int readConnectionId = -1;
                        private int command = -1;

                        private int readIpLength = -1;
                        private byte[] readIp = null;
                        private int readPort = -1;
                        private int readLength = -1;

                        private int readByte(int old, ByteBuffer receivedBuffer) {
                            if (old >= 0) {
                                return old;
                            }
                            if (!receivedBuffer.hasRemaining()) {
                                return -1;
                            }
                            return receivedBuffer.get() & 0xFF;
                        }

                        private byte[] readBytes(ByteBuffer receivedBuffer, int len) {
                            if (readByteBuffer == null) {
                                readByteBuffer = ByteBuffer.allocate(len);
                            }
                            int l = len - readByteBuffer.position();
                            if (l > receivedBuffer.remaining()) {
                                l = receivedBuffer.remaining();
                            }
                            System.arraycopy(receivedBuffer.array(), receivedBuffer.arrayOffset() + receivedBuffer.position(), readByteBuffer.array(), readByteBuffer.arrayOffset() + readByteBuffer.position(), l);
                            receivedBuffer.position(receivedBuffer.position() + l);
                            readByteBuffer.position(readByteBuffer.position() + l);
                            if (readByteBuffer.position() == readByteBuffer.capacity()) {
                                byte[] b = readByteBuffer.array();
                                readByteBuffer = null;
                                return b;
                            }
                            return null;
                        }

                        private int readInt(int old, ByteBuffer receivedBuffer) {
                            if (old >= 0) {
                                return old;
                            }
                            byte[] r = readBytes(receivedBuffer, Ints.BYTES);
                            if (r == null) {
                                return -1;
                            }
                            return ByteBuffer.wrap(r).getInt();
                        }

                        private byte[] readBytes(byte[] old, ByteBuffer receivedBuffer, int len) {
                            if (old != null) {
                                return old;
                            }
                            return readBytes(receivedBuffer, len);
                        }

                        @Override
                        public void received(Address receivedAddress, final ByteBuffer receivedBuffer) {
                            proxyExecutor.execute(() -> {
                                while (true) {
                                    command = readByte(command, receivedBuffer);
                                    if (command < 0) {
                                        return;
                                    }

                                    readConnectionId = readInt(readConnectionId, receivedBuffer);
                                    if (readConnectionId < 0) {
                                        return;
                                    }

                                    switch (command) {
                                        case ProxyCommons.Commands.SEND_WITH_ADDRESS: {
                                            readIpLength = readInt(readIpLength, receivedBuffer);
                                            if (readIpLength < 0) {
                                                return;
                                            }
                                            readIp = readBytes(readIp, receivedBuffer, readIpLength);
                                            if (readIp == null) {
                                                return;
                                            }
                                            readPort = readInt(readPort, receivedBuffer);
                                            if (readPort < 0) {
                                                return;
                                            }
                                            readLength = readInt(readLength, receivedBuffer);
                                            if (readLength < 0) {
                                                return;
                                            }
                                            // LOGGER.debug("SEND_WITH_ADDRESS {}:{} [{} bytes]", Address.ipToString(readIp), readPort, readLength);
                                            byte[] r = readBytes(receivedBuffer, readLength);
                                            if (r == null) {
                                                return;
                                            }
                                            InnerConnection receivedInnerConnection = connections.get(readConnectionId);
                                            if (receivedInnerConnection != null) {
                                                inTracker.track(Address.ipToString(readIp), addr ->
                                                        String.format("Received from %s via Proxy", addr));
                                                receivedInnerConnection.connection.received(new Address(readIp, readPort), ByteBuffer.wrap(r));
                                            }
                                            readConnectionId = -1;
                                            command = -1;
                                            readIpLength = -1;
                                            readIp = null;
                                            readPort = -1;
                                            readLength = -1;
                                            break;
                                        }
                                        case ProxyCommons.Commands.SEND_WITHOUT_ADDRESS: {
                                            readLength = readInt(readLength, receivedBuffer);
                                            if (readLength < 0) {
                                                return;
                                            }
                                            // LOGGER.debug("SEND_WITHOUT_ADDRESS [{} bytes]", readLength);
                                            byte[] r = readBytes(receivedBuffer, readLength);
                                            if (r == null) {
                                                return;
                                            }
                                            InnerConnection receivedInnerConnection = connections.get(readConnectionId);
                                            if (receivedInnerConnection != null) {
                                                receivedInnerConnection.connection.received(null, ByteBuffer.wrap(r));
                                            }
                                            readConnectionId = -1;
                                            command = -1;
                                            readLength = -1;
                                            break;
                                        }
                                        case ProxyCommons.Commands.CLOSE: {
                                            InnerConnection receivedInnerConnection = connections.remove(readConnectionId);
                                            readConnectionId = -1;
                                            command = -1;
                                            if (receivedInnerConnection != null) {
                                                receivedInnerConnection.connection.closed();
                                            }
                                            break;
                                        }
                                    }
                                }
                            });
                        }
                    });
                }

                innerConnection.connection = callback;

                byte[] headerAsBytes = header.toString().getBytes(Charsets.UTF_8);

                SendCallback sendCallback = new SendCallback() {
                    @Override
                    public void failed(IOException e) {
                        LOGGER.error("error: " + e);
                        doClose();
                    }

                    @Override
                    public void sent() {
                    }
                };

                if (connectAddress == null) {
                    ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + headerAsBytes.length);
                    b.put((byte) ProxyCommons.Commands.CONNECT_WITHOUT_ADDRESS);
                    b.putInt(innerConnection.connectionId);
                    b.putInt(headerAsBytes.length);
                    b.put(headerAsBytes);
                    b.flip();
                    proxyConnector.send(null, b, sendCallback);
                } else {
                    ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + connectAddress.ip.length + Ints.BYTES + Ints.BYTES + headerAsBytes.length);
                    b.put((byte) ProxyCommons.Commands.CONNECT_WITH_ADDRESS);
                    b.putInt(innerConnection.connectionId);
                    b.putInt(connectAddress.ip.length);
                    b.put(connectAddress.ip);
                    b.putInt(connectAddress.port);
                    b.putInt(headerAsBytes.length);
                    b.put(headerAsBytes);
                    b.flip();
                    proxyConnector.send(null, b, sendCallback);
                }

                callback.connected(null);
            });
        }

        @Override
        public void send(final Address sendAddress, final ByteBuffer sendBuffer, final SendCallback callback) {
            proxyExecutor.execute(() -> {
                if (innerConnection.connection == null) {
                    throw new IllegalStateException("send() must be called after connect()");
                }

                if (proxyConnector == null) {
                    callback.failed(new IOException("Connection lost"));
                    return;
                }

                if (sendAddress == null) {
                    // LOGGER.debug("-->SEND_WITHOUT_ADDRESS [{} bytes]", sendBuffer.remaining());
                    ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + sendBuffer.remaining());
                    b.put((byte) ProxyCommons.Commands.SEND_WITHOUT_ADDRESS);
                    b.putInt(innerConnection.connectionId);
                    b.putInt(sendBuffer.remaining());
                    b.put(sendBuffer);
                    b.flip();
                    proxyConnector.send(null, b, callback);
                } else {
                    outTracker.track(Address.ipToString(sendAddress.ip), addr ->
                            String.format("Sending to %s via Proxy ", addr));
                    // LOGGER.debug("-->SEND_WITH_ADDRESS {} [{} bytes]", sendAddress, sendBuffer.remaining());
                    ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + sendAddress.ip.length + Ints.BYTES + Ints.BYTES + sendBuffer.remaining());
                    b.put((byte) ProxyCommons.Commands.SEND_WITH_ADDRESS);
                    b.putInt(innerConnection.connectionId);
                    b.putInt(sendAddress.ip.length); //TODO Reduce to short?
                    b.put(sendAddress.ip);
                    b.putInt(sendAddress.port);
                    b.putInt(sendBuffer.remaining());
                    b.put(sendBuffer);
                    b.flip();
                    proxyConnector.send(null, b, callback);
                }
            });
        }

        @Override
        public void close() {
            proxyExecutor.execute(() -> {
                connections.remove(innerConnection.connectionId);

                if (proxyConnector != null) {
                    ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES);
                    b.put((byte) ProxyCommons.Commands.CLOSE);
                    b.putInt(innerConnection.connectionId);
                    b.flip();

                    SendCallback sendCallback = new SendCallback() {
                        @Override
                        public void failed(IOException e) {
                            doClose();
                        }

                        @Override
                        public void sent() {
                        }
                    };

                    proxyConnector.send(null, b, sendCallback);
                }

                innerConnection.connection.closed();
            });
        }
    }
}
