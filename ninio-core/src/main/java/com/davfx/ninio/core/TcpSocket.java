package com.davfx.ninio.core;

import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.core.supervision.metrics.DisplayableMetricsManager;
import com.davfx.ninio.core.supervision.metrics.Metric;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

public final class TcpSocket implements Connecter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpSocket.class);

    private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(TcpSocket.class.getPackage().getName());
    private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("tcp.buffer.write").longValue();
    private static final double SOCKET_TIMEOUT = ConfigUtils.getDuration(CONFIG, "tcp.socket.timeout");
    private static final long SOCKET_WRITE_BUFFER_SIZE = CONFIG.getBytes("tcp.socket.write").longValue();
    private static final long SOCKET_READ_BUFFER_SIZE = CONFIG.getBytes("tcp.socket.read").longValue();

    private final AtomicLong max = new AtomicLong(0L);

    private void setWriteMax(long newMax) {
        while (true) {
            long curMax = max.get();
            if (curMax >= newMax) {
                break;
            }
            if (max.compareAndSet(curMax, newMax)) {
                break;
            }
        }
    }


    public interface Builder extends NinioBuilder<Connecter> {
        Builder with(ByteBufferAllocator byteBufferAllocator);

        Builder bind(Address bindAddress);

        Builder to(Address connectAddress);
    }

    public static Builder builder() {
        return new Builder() {
            private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator();

            private Address bindAddress = null;
            private Address connectAddress = null;

            @Override
            public Builder with(ByteBufferAllocator byteBufferAllocator) {
                this.byteBufferAllocator = byteBufferAllocator;
                return this;
            }

            @Override
            public Builder bind(Address bindAddress) {
                this.bindAddress = bindAddress;
                return this;
            }

            @Override
            public Builder to(Address connectAddress) {
                this.connectAddress = connectAddress;
                return this;
            }

            @Override
            public Connecter create(NinioProvider ninioProvider) {
                if (connectAddress == null) {
                    throw new NullPointerException("connectAddress");
                }
                return new TcpSocket(ninioProvider.queue(NinioPriority.REGULAR), byteBufferAllocator, bindAddress, connectAddress);
            }
        };
    }

    private static final class ToWrite {
        public final ByteBuffer buffer;
        public final SendCallback callback;

        public ToWrite(ByteBuffer buffer, SendCallback callback) {
            this.buffer = buffer;
            this.callback = callback;
        }
    }

    private final Queue queue;
    private final ByteBufferAllocator byteBufferAllocator;
    private final Address bindAddress;
    private final Address connectAddress;

    private SocketChannel currentChannel = null;
    private SelectionKey currentInboundKey = null;
    private SelectionKey currentSelectionKey = null;

    private final Deque<ToWrite> toWriteQueue = new LinkedList<>();
    private long toWriteLength = 0L;

    private Connection connectCallback = null;
    private boolean closed = false;

    private TcpSocket(Queue queue, ByteBufferAllocator byteBufferAllocator, Address bindAddress, Address connectAddress) {
        this.queue = queue;
        this.byteBufferAllocator = byteBufferAllocator;
        this.bindAddress = bindAddress;
        this.connectAddress = connectAddress;
        DisplayableMetricsManager.instance().addIfAbsent(new Metric("[TCP]") {
            @Override
            public String getValue() {
                long currentMax = max.getAndSet(0L);
                return Long.toString(currentMax);
            }

            @Override
            public void reset() {
                max.set(0L);
            }
        });
    }

    @Override
    public void connect(final Connection callback) {
        queue.execute(() -> {
            try {
                if (closed) {
                    throw new IOException("Closed");
                }
                if (currentChannel != null) {
                    throw new IllegalStateException("connect() cannot be called twice");
                }

                final SocketChannel channel = SocketChannel.open();
                currentChannel = channel;
                try {
                    channel.configureBlocking(false);
                    if (SOCKET_TIMEOUT > 0d) {
                        channel.socket().setSoTimeout((int) (SOCKET_TIMEOUT * 1000d)); // Not working with NIO?
                    }
                    if (SOCKET_READ_BUFFER_SIZE > 0L) {
                        channel.socket().setReceiveBufferSize((int) SOCKET_READ_BUFFER_SIZE);
                    }
                    if (SOCKET_WRITE_BUFFER_SIZE > 0L) {
                        channel.socket().setSendBufferSize((int) SOCKET_WRITE_BUFFER_SIZE);
                    }
                    final SelectionKey inboundKey = queue.register(channel);
                    inboundKey.interestOps(inboundKey.interestOps() | SelectionKey.OP_CONNECT);
                    currentInboundKey = inboundKey;
                    inboundKey.attach((SelectionKeyVisitor) key -> {
                        if (closed) {
                            disconnect(channel, inboundKey, null, callback, null);
                            return;
                        }

                        if (!key.isConnectable()) {
                            return;
                        }

                        try {
                            channel.finishConnect();
                            final SelectionKey selectionKey = queue.register(channel);
                            currentSelectionKey = selectionKey;

                            selectionKey.attach(new SelectionKeyVisitor() {
                                @Override
                                public void visit(SelectionKey key) {
                                    if (closed) {
                                        disconnect(channel, inboundKey, null, callback, null);
                                        return;
                                    }

                                    if (!channel.isOpen()) {
                                        return;
                                    }

                                    if (key.isReadable()) {
                                        while (true) {
                                            ByteBuffer readBuffer = byteBufferAllocator.allocate();
                                            try {
                                                int r = channel.read(readBuffer);
                                                LOGGER.trace("Reading: {} bytes", r);
                                                if (r == 0) {
                                                    break;
                                                }
                                                if (r < 0) {
                                                    LOGGER.trace("Connection closed by peer");
                                                    disconnect(channel, inboundKey, selectionKey, callback, null);
                                                    return;
                                                }
                                            } catch (IOException e) {
                                                LOGGER.trace("Read failed", e);
                                                disconnect(channel, inboundKey, selectionKey, callback, e);
                                                return;
                                            }

                                            readBuffer.flip();
                                            callback.received(null, readBuffer);
                                        }
                                    } else if (key.isWritable()) {
                                        while (true) {
                                            ToWrite toWrite = toWriteQueue.peek();
                                            if (toWrite == null) {
                                                break;
                                            }

                                            if (toWrite.buffer == null) {
                                                try {
                                                    channel.close();
                                                } catch (IOException e) {
                                                    LOGGER.trace("Graceful close failed", e);
                                                    toWrite.callback.failed(e);
                                                    disconnect(channel, inboundKey, selectionKey, callback, e);
                                                    return;
                                                }
                                            } else {
                                                long size = toWrite.buffer.remaining();

                                                try {
                                                    LOGGER.trace("Actual write buffer: {} bytes", size);
                                                    channel.write(toWrite.buffer);
                                                    toWriteLength -= size - toWrite.buffer.remaining();
                                                } catch (IOException e) {
                                                    LOGGER.trace("Write failed", e);
                                                    toWrite.callback.failed(e);
                                                    disconnect(channel, inboundKey, selectionKey, callback, e);
                                                    return;
                                                }

                                                if (toWrite.buffer.hasRemaining()) {
                                                    return;
                                                }
                                            }

                                            toWriteQueue.remove();
                                            toWrite.callback.sent();
                                        }
                                        if (!channel.isOpen()) {
                                            return;
                                        }
                                        if (!selectionKey.isValid()) {
                                            return;
                                        }
                                        selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
                                    }
                                }
                            });

                            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
                            if (!toWriteQueue.isEmpty()) {
                                selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                            }

                        } catch (IOException e) {
                            LOGGER.trace("Connection failed", e);
                            disconnect(channel, inboundKey, null, null, e);
                            callback.failed(e);
                            return;
                        }

                        callback.connected(null);
                    });

                    if (bindAddress != null) {
                        try {
                            channel.socket().bind(new InetSocketAddress(InetAddress.getByAddress(bindAddress.ip), bindAddress.port));
                        } catch (IOException e) {
                            disconnect(channel, inboundKey, null, null, e);
                            callback.failed(new IOException("Could not bind to: " + bindAddress, e));
                            return;
                        }
                    }

                    try {
                        InetSocketAddress a = new InetSocketAddress(InetAddress.getByAddress(connectAddress.ip), connectAddress.port);
                        LOGGER.trace("Connecting to: {}", a);
                        channel.connect(a);
                    } catch (IOException e) {
                        disconnect(channel, inboundKey, null, null, e);
                        callback.failed(new IOException("Could not connect to: " + connectAddress, e));
                        return;
                    }

                } catch (IOException e) {
                    disconnect(channel, null, null, null, e);
                    callback.failed(e);
                    return;
                }

                connectCallback = callback;

            } catch (IOException ee) {
                callback.failed(ee);
                return;
            }
        });
    }

    @Override
    public void close() {
        queue.execute(() -> disconnect(currentChannel, currentInboundKey, currentSelectionKey, connectCallback, null));
    }

    @Override
    public void send(final Address address, final ByteBuffer buffer, final SendCallback callback) {
        queue.execute(() -> {
            if (closed) {
                callback.failed(new IOException("Closed"));
                return;
            }

            if (address != null) {
                LOGGER.warn("Ignored send address: {}", address);
            }

            if (buffer != null) {
                if ((WRITE_MAX_BUFFER_SIZE > 0L) && (toWriteLength > WRITE_MAX_BUFFER_SIZE)) {
                    LOGGER.warn("Dropping {} bytes that should have been sent to {}", buffer.remaining(), address);
                    callback.failed(new IOException("Packet dropped"));
                    return;
                }
            }

            toWriteQueue.add(new ToWrite(buffer, callback));
            if (buffer != null) {
                toWriteLength += buffer.remaining();
                LOGGER.trace("Write buffer: {} bytes (current size: {} bytes)", buffer.remaining(), toWriteLength);
                setWriteMax(toWriteLength);
            }

            SocketChannel channel = currentChannel;
            SelectionKey selectionKey = currentSelectionKey;
            if (channel == null) {
                return;
            }
            if (selectionKey == null) {
                return;
            }
            if (!channel.isOpen()) {
                return;
            }
            if (!selectionKey.isValid()) {
                return;
            }
            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
        });
    }

    private void disconnect(SocketChannel channel, SelectionKey inboundKey, SelectionKey selectionKey, Connection callback, IOException error) {
        if (channel != null) {
            try {
                channel.socket().close();
            } catch (IOException e) {
            }
            try {
                channel.close();
            } catch (IOException e) {
            }
        }
        if (inboundKey != null) {
            inboundKey.cancel();
        }
        if (selectionKey != null) {
            selectionKey.cancel();
        }

        IOException e = (error == null) ? new IOException("Closed") : new IOException("Closed because of", error);
        for (ToWrite toWrite : toWriteQueue) {
            toWrite.callback.failed(e);
        }
        toWriteQueue.clear();

        currentChannel = null;
        currentInboundKey = null;
        currentSelectionKey = null;

        if (!closed) {
            closed = true;

            if (callback != null) {
                callback.closed();
            }
        }
    }

}
