package com.davfx.ninio.core;

import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.core.supervision.metrics.DisplayableMetricsManager;
import com.davfx.ninio.core.supervision.metrics.MetricsParams;
import com.davfx.ninio.core.supervision.tracking.RequestTracker;
import com.davfx.ninio.core.supervision.tracking.RequestTrackerManager;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.DateUtils;
import com.davfx.ninio.util.Wait;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Random;

// MacOS X : sudo sysctl -w net.inet.udp.recvspace=8000000
// Linux: sysctl -w net.core.rmem_max=8000000
public final class UdpSocket implements Connecter {

    public static final class Test {
        public static final class Send {
            private static final Logger INNER_LOGGER = LoggerFactory.getLogger(Receive.class);

            private static double floorTime(double now, double period) {
                double precision = 1000d;
                long t = (long) (now * precision);
                long d = (long) (period * precision);
                return (t - (t % d)) / precision;
            }

            public static void main(String[] args) throws Exception {
                try (DatagramSocket s = new DatagramSocket()) {
                    InetAddress a = InetAddress.getByName(System.getProperty("host", "127.0.0.1"));
                    int port = Integer.parseInt(System.getProperty("port", "9099"));
                    byte[] buffer = new byte[Integer.parseInt(System.getProperty("size", "100"))];
                    Random random = new Random(System.currentTimeMillis());
                    random.nextBytes(buffer);
                    double t = Double.parseDouble(System.getProperty("wait", "0"));
                    long k = Long.parseLong(System.getProperty("burst", "1000000"));
                    double display = Double.parseDouble(System.getProperty("display", "10"));
                    boolean stop = Boolean.parseBoolean(System.getProperty("stop", "true"));
                    long n = 0L;
                    INNER_LOGGER.info("Sending to {}:{}", a, port);

                    double now = DateUtils.now();
                    double timeToDisplay = stop ? 0d : now + display - (now - floorTime(now, display));
                    double last = now;

                    while (true) {
                        for (int i = 0; i < k; i++) {
                            DatagramPacket p = new DatagramPacket(buffer, buffer.length, a, port);
                            s.send(p);
                            n++;
                        }
                        if (t > 0d) {
                            Thread.sleep((long) (t * 1000d));
                        }
                        double tn = DateUtils.now();
                        if (tn >= timeToDisplay) {
                            INNER_LOGGER.info("{} packets sent ({} KBps per second)", n, Math.round(100d * ((n * buffer.length) / 1000d) / (tn - last)) / 100d);
                            n = 0L;
                            timeToDisplay += display;
                            last = tn;
                            if (stop) {
                                break;
                            }
                        }
                    }
                }
            }
        }

        public static final class Receive {
            private static final Logger INNER_LOGGER = LoggerFactory.getLogger(Receive.class);

            public static void main(String[] args) throws Exception {
                InetAddress a = InetAddress.getByName(System.getProperty("host", "0.0.0.0"));
                int port = Integer.parseInt(System.getProperty("port", "9099"));

                try (Ninio ninio = Ninio.create()) {
                    try (Connecter server = ninio.create(UdpSocket.builder().bind(new Address(a.getAddress(), port)))) {
                        server.connect(
                                new Connection() {
                                    @Override
                                    public void failed(IOException ioe) {
                                        INNER_LOGGER.error("Failed", ioe);
                                    }

                                    @Override
                                    public void connected(Address address) {
                                    }

                                    @Override
                                    public void closed() {
                                    }

                                    @Override
                                    public void received(Address address, ByteBuffer buffer) {
                                    }
                                });
                        new Wait().waitFor();
                    }
                }
            }
        }
    }


    private static final Logger LOGGER = LoggerFactory.getLogger(UdpSocket.class);

    private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(UdpSocket.class.getPackage().getName());
    private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("udp.buffer.write").longValue();
    private static final long SOCKET_WRITE_BUFFER_SIZE = CONFIG.getBytes("udp.socket.write").longValue();
    private static final long SOCKET_READ_BUFFER_SIZE = CONFIG.getBytes("udp.socket.read").longValue();

    public interface Builder extends NinioBuilder<Connecter> {
        Builder with(ByteBufferAllocator byteBufferAllocator);

        Builder bind(Address bindAddress);
    }

    public static Builder builder() {
        return new Builder() {
            private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator();

            private Address bindAddress = null;

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
            public Connecter create(NinioProvider ninioProvider) {
                return new UdpSocket(ninioProvider.queue(NinioPriority.HIGH), byteBufferAllocator, bindAddress);
            }
        };
    }

    private static final class ToWrite {
        public final Address address;
        public final ByteBuffer buffer;
        public final SendCallback callback;

        public ToWrite(Address address, ByteBuffer buffer, SendCallback callback) {
            this.address = address;
            this.buffer = buffer;
            this.callback = callback;
        }
    }

    private final Queue queue;
    private final ByteBufferAllocator byteBufferAllocator;
    private final Address bindAddress;
    private DatagramChannel currentChannel = null;
    private SelectionKey currentSelectionKey = null;

    private final Deque<ToWrite> toWriteQueue = new LinkedList<>();
    private long toWriteLength = 0L;

    private Connection connectCallback = null;
    private boolean closed = false;

    private final RequestTracker inTracker;
    private final RequestTracker outTracker;

    public UdpSocket(Queue queue, ByteBufferAllocator byteBufferAllocator, Address bindAddress) {
        this.queue = queue;
        this.byteBufferAllocator = byteBufferAllocator;
        this.bindAddress = bindAddress;
        String prefix = "UDP";
        inTracker = RequestTrackerManager.instance().getTracker(prefix, "in");
        outTracker = RequestTrackerManager.instance().getTracker(prefix, "out");
        DisplayableMetricsManager.instance().percent(outTracker, inTracker, prefix, "lost");
    }

    @Override
    public void connect(final Connection callback) {
        queue.execute(() -> {
            if (currentChannel != null) {
                throw new IllegalStateException("connect() cannot be called twice");
            }
            if (currentSelectionKey != null) {
                throw new IllegalStateException();
            }

            //%% LOGGER.debug("Connecting UDP socket");
            try {
                if (closed) {
                    throw new IOException("Closed");
                }

                final DatagramChannel channel = DatagramChannel.open();
                currentChannel = channel;
                try {
                    channel.configureBlocking(false);
                    if (SOCKET_READ_BUFFER_SIZE > 0L) {
                        channel.socket().setReceiveBufferSize((int) SOCKET_READ_BUFFER_SIZE);
                    }
                    if (SOCKET_WRITE_BUFFER_SIZE > 0L) {
                        channel.socket().setSendBufferSize((int) SOCKET_WRITE_BUFFER_SIZE);
                    }
                    final SelectionKey selectionKey = queue.register(channel);
                    currentSelectionKey = selectionKey;

                    selectionKey.attach((SelectionKeyVisitor) key -> {
                        if (closed) {
                            disconnect(channel, selectionKey, callback, null);
                            return;
                        }

                        if (!channel.isOpen()) {
                            return;
                        }

                        if (key.isReadable()) {
                            while (true) {
                                ByteBuffer readBuffer = byteBufferAllocator.allocate();
                                InetSocketAddress from;
                                try {
                                    from = (InetSocketAddress) channel.receive(readBuffer);
                                    if (from == null) {
                                        break;
                                    }
                                } catch (IOException e) {
                                    LOGGER.trace("Read failed", e);
                                    disconnect(channel, selectionKey, callback, e);
                                    return;
                                }

                                if (!readBuffer.hasRemaining()) {
                                    LOGGER.error("Packet received too big: {} bytes", readBuffer.position());
                                }

                                readBuffer.flip();
                                Address a = new Address(from.getAddress().getAddress(), from.getPort());

                                inTracker.track(from.getAddress().getHostAddress(), addr -> String.format("Received from %s", addr));
                                long start = System.nanoTime();
                                while ((start + 100000L) >= System.nanoTime()) ;
                                callback.received(a, readBuffer);
                            }
                        } else if (key.isWritable()) {
                            while (true) {
                                ToWrite toWrite = toWriteQueue.peek();
                                if (toWrite == null) {
                                    break;
                                }

                                if (toWrite.buffer != null) {
                                    long size = toWrite.buffer.remaining();

                                    if (toWrite.address == null) {
                                        try {
                                            LOGGER.trace("Actual write buffer: {} bytes", size);
                                            channel.write(toWrite.buffer);
                                            if (toWrite.buffer.hasRemaining()) {
                                                throw new IOException("Packet was not entirely written");
                                            }

                                        } catch (IOException e) {
                                            LOGGER.trace("Write failed", e);
                                            //%% disconnect(channel, selectionKey, callback);
                                            //%% return;
                                            toWriteLength -= size;
                                            toWriteQueue.remove();
                                            toWrite.callback.failed(e);
                                            continue;
                                        }
                                    } else {
                                        InetSocketAddress a;
                                        try {
                                            a = new InetSocketAddress(InetAddress.getByAddress(toWrite.address.ip), toWrite.address.port);
                                        } catch (IOException e) {
                                            LOGGER.warn("Invalid address: {}", toWrite.address);
                                            LOGGER.trace("Write failed", e);
                                            //%% disconnect(channel, selectionKey, callback);
                                            //%% return;
                                            toWriteLength -= size;
                                            toWriteQueue.remove();
                                            toWrite.callback.failed(e);
                                            continue;
                                        }

                                        try {
                                            LOGGER.trace("Actual write buffer: {} bytes", size);
                                            channel.send(toWrite.buffer, a);
                                            if (toWrite.buffer.hasRemaining()) {
                                                throw new IOException("Packet was not entirely written");
                                            }

                                            outTracker.track(a.getAddress().getHostAddress(), addr -> String.format("Sending to %s", addr));

                                            toWriteLength -= size; //%% - toWrite.buffer.remaining();
                                        } catch (IOException e) {
                                            LOGGER.trace("Write failed", e);
                                            //%% disconnect(channel, selectionKey, callback);
                                            //%% return;
                                            toWriteLength -= size;
                                            toWriteQueue.remove();
                                            toWrite.callback.failed(e);
                                            continue;
                                        }
                                    }
                                }

                                //%% if (toWrite.buffer.hasRemaining()) {
                                //%% return;
                                //%% }
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
                    });

                    selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
                    if (!toWriteQueue.isEmpty()) {
                        selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                    }

                    if (bindAddress != null) {
                        try {
                            channel.socket().bind(new InetSocketAddress(InetAddress.getByAddress(bindAddress.ip), bindAddress.port));
                        } catch (IOException e) {
                            disconnect(channel, selectionKey, null, e);
                            callback.failed(new IOException("Could not bind to: " + bindAddress, e));
                            return;
                        }
                    }

                } catch (IOException e) {
                    disconnect(channel, null, null, e);
                    callback.failed(e);
                    return;
                }

                connectCallback = callback;

            } catch (IOException e) {
                callback.failed(e);
                return;
            }

            callback.connected(null);
        });
    }

    @Override
    public void close() {
        queue.execute(() -> disconnect(currentChannel, currentSelectionKey, connectCallback, null));
    }

    @Override
    public void send(final Address address, final ByteBuffer buffer, final SendCallback callback) {
        queue.execute(() -> {
            if (closed) {
                callback.failed(new IOException("Closed"));
                return;
            }

            if (buffer != null) {
                if ((WRITE_MAX_BUFFER_SIZE > 0L) && (toWriteLength > WRITE_MAX_BUFFER_SIZE)) {
                    LOGGER.warn("Dropping {} bytes that should have been sent to {}", buffer.remaining(), address);
                    callback.failed(new IOException("Packet dropped"));
                    return;
                }
            }

            toWriteQueue.add(new ToWrite(address, buffer, callback));
            if (buffer != null) {
                toWriteLength += buffer.remaining();
                LOGGER.trace("Write buffer: {} bytes (to {}) (current size: {} bytes)", buffer.remaining(), address, toWriteLength);
            }

            DatagramChannel channel = currentChannel;
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

    private void disconnect(DatagramChannel channel, SelectionKey selectionKey, Connection callback, IOException error) {
        if (channel != null) {
            channel.socket().close();
            try {
                channel.close();
            } catch (IOException e) {
            }
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
        currentSelectionKey = null;

        if (!closed) {
            closed = true;

            if (callback != null) {
                callback.closed();
            }
        }
    }
}
