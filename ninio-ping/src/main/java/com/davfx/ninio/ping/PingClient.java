package com.davfx.ninio.ping;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.NinioProvider;
import com.davfx.ninio.core.Nop;
import com.davfx.ninio.core.RawSocket;
import com.davfx.ninio.core.supervision.metrics.DisplayableMetricsManager;
import com.davfx.ninio.core.supervision.metrics.MetricsParams;
import com.davfx.ninio.core.supervision.tracking.RequestTracker;
import com.davfx.ninio.core.supervision.tracking.RequestTrackerManager;
import com.davfx.ninio.string.Identifiers;
import com.davfx.ninio.util.Mutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public final class PingClient implements PingConnecter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PingClient.class);

    private static final int ICMP_PROTOCOL = 1;
    private static final long ID_LIMIT = Integer.MAX_VALUE - 1; // Last one is reserved for TERMINATE packet on RawSocket, sorry...

    public interface Builder extends NinioBuilder<PingConnecter> {
        @Deprecated
        Builder with(Executor executor);

        Builder with(RawSocket.Builder connectorFactory);
    }

    public static Builder builder() {
        return new Builder() {
            private RawSocket.Builder connectorFactory = RawSocket.builder();

            @Deprecated
            @Override
            public Builder with(Executor executor) {
                return this;
            }

            @Override
            public Builder with(RawSocket.Builder connectorFactory) {
                this.connectorFactory = connectorFactory;
                return this;
            }

            @Override
            public PingConnecter create(NinioProvider ninioProvider) {
                return new PingClient(ninioProvider.executor(), connectorFactory.protocol(ICMP_PROTOCOL).create(ninioProvider));
            }
        };
    }

    private final Executor executor;
    private final Connecter connecter;
    private long nextId = 65436;

    private final Map<Address, PingReceiver> receivers = new HashMap<>();

    private boolean closed = false;

    private final String clientIdentifier;
    private final RequestTracker outTracker;
    private final RequestTracker inTracker;

    public PingClient(Executor executor, Connecter connecter) {
        this.executor = executor;
        this.connecter = connecter;
        clientIdentifier = Identifiers.identifier();
        String prefix = "PING";
        outTracker = RequestTrackerManager.instance().getTracker(prefix, "out");
        inTracker = RequestTrackerManager.instance().getTracker(prefix, "in");
        DisplayableMetricsManager.instance().percent(outTracker, inTracker, prefix, "lost");
    }

    private static void closeSendCallbacks(Map<Address, PingReceiver> receivers) {
        IOException e = new IOException("Closed");
        for (PingReceiver c : receivers.values()) {
            c.failed(e);
        }
        receivers.clear();
    }

    @Override
    public void connect(final PingConnection callback) {
        connecter.connect(new Connection() {
            @Override
            public void received(final Address address, final ByteBuffer buffer) {
                executor.execute(() -> {
                    if (closed) {
                        return;
                    }

                    int type;
                    int code;
                    long now = System.nanoTime();
                    long time;
                    Address id;
                    try {
                        type = buffer.get() & 0xFF; // type
                        code = buffer.get() & 0xFF; // code
                        if ((type != 0) || (code != 0)) {
                            return;
                        }
                        buffer.getShort(); // checksum
                        short identifier = buffer.getShort(); // identifier
                        short sequence = buffer.getShort(); // sequence
                        time = buffer.getLong();
                        id = new Address(address.ip, (int) (((identifier & 0xFFFFL) << 16) | (sequence & 0xFFFFL)));
                    } catch (Exception e) {
                        LOGGER.error("Invalid packet", e);
                        return;
                    }

                    long deltaNano = now - time;
                    double delta = deltaNano / 1_000_000_000d;

                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("@{} Received ICMP packet [{}/{}] from {} (ID {}): {} ns", clientIdentifier, type, code, address, id.port, deltaNano);
                    }

                    PingReceiver r = receivers.remove(id);
                    if (r == null) {
                        return;
                    }

                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("@{} Transmitted ICMP packet [{}/{}] from {} (ID {}): {} ns", clientIdentifier, type, code, address, id.port, deltaNano);
                    }

                    inTracker.track(Address.ipToString(address.ip), addr ->
                            String.format("Received ping from %s ", addr));
                    r.received(delta);
                });
            }

            @Override
            public void failed(final IOException ioe) {
                executor.execute(() -> {
                    if (closed) {
                        return;
                    }

                    closed = true;
                    closeSendCallbacks(receivers);
                    callback.failed(ioe);
                });
            }

            @Override
            public void connected(final Address address) {
                executor.execute(() -> {
                    if (closed) {
                        return;
                    }

                    callback.connected(address);
                });
            }

            @Override
            public void closed() {
                executor.execute(() -> {
                    if (closed) {
                        return;
                    }

                    closed = true;
                    closeSendCallbacks(receivers);
                    callback.closed();
                });
            }
        });
    }

    @Override
    public Cancelable ping(final byte[] ip, final PingReceiver callback) {
        final Mutable<Address> id = new Mutable<>();

        executor.execute(() -> {
            if (closed) {
                callback.failed(new IOException("Closed"));
                return;
            }

            id.value = new Address(ip, (int) (nextId & 0xFFFFFFFFL));
            nextId++;
            if (nextId == ID_LIMIT) {
                nextId = 65436;
            }
            receivers.put(id.value, callback);

            byte[] sendData = new byte[16];

            ByteBuffer b = ByteBuffer.wrap(sendData);
            b.put((byte) 8); // requestType (Echo)
            b.put((byte) 0); // code
            int checksumPosition = b.position();
            b.putShort((short) 0); // checksum
            b.putShort((short) ((id.value.port >>> 16) & 0xFFFF)); // identifier
            b.putShort((short) (id.value.port & 0xFFFF)); // sequence
            long nt = System.nanoTime();
            b.putLong(nt);
            int endPosition = b.position();

            b.position(0);
            int checksum = 0;
            while (b.position() < endPosition) {
                checksum += b.getShort() & 0xFFFF;
            }
            while ((checksum & 0xFFFF0000) != 0) {
                checksum = (checksum & 0xFFFF) + (checksum >>> 16);
            }

            checksum = (~checksum & 0xffff);
            b.position(checksumPosition);
            b.putShort((short) (checksum & 0xFFFF));
            b.position(endPosition);
            b.flip();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("@{} Sending ICMP packet to {} (ID {})", clientIdentifier, Address.ipToString(ip), id.value.port);
            }
            Address address = new Address(ip, 0);
            outTracker.track(Address.ipToString(address.ip), addr ->
                    String.format("Sending ping to %s ", addr));
            connecter.send(address, b, new Nop());
        });

        return () -> executor.execute(() -> {
            if (id.value == null) {
                return;
            }

            PingReceiver r = receivers.remove(id.value);
            if (r == null) {
                return;
            }
            r.failed(new IOException("Canceled"));
        });
    }

    @Override
    public void close() {
        connecter.close();

        executor.execute(() -> {
            if (closed) {
                return;
            }

            closeSendCallbacks(receivers);
        });
    }
}
