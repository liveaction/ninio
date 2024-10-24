package com.davfx.ninio.core;

import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.core.supervision.metrics.DisplayableMetricsManager;
import com.davfx.ninio.core.supervision.metrics.MetricsParams;
import com.davfx.ninio.core.supervision.tracking.RequestTracker;
import com.davfx.ninio.core.supervision.tracking.RequestTrackerManager;
import com.davfx.ninio.util.ClassThreadFactory;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.Wait;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public final class TcpdumpSocket implements Connecter {

    public static final class Test {
        public static final class Receive {
            private static final Logger INNER_LOGGER = LoggerFactory.getLogger(Receive.class);

            // sudo chmod go=r /dev/bpf*
            public static void main(String[] args) throws Exception {
                InetAddress a = InetAddress.getByName(System.getProperty("host", "0.0.0.0"));
                int port = Integer.parseInt(System.getProperty("port", "9099"));

                String interfaceId = System.getProperty("interface", "lo");
                String modeString = System.getProperty("mode", "raw");
                TcpdumpMode mode;
                if (modeString.equals("hex")) {
                    mode = TcpdumpMode.HEX;
                } else if (modeString.equals("raw")) {
                    mode = TcpdumpMode.RAW;
                } else {
                    throw new Exception("Bad mode (only hex|raw allowed): " + modeString);
                }
                String rule = System.getProperty("rule", "dst port " + port);

                try (Ninio ninio = Ninio.create()) {
                    try (Connecter server = ninio.create(TcpdumpSocket.builder().on(interfaceId).mode(mode).rule(rule).bind(new Address(a.getAddress(), port)))) {
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

    public interface Builder extends NinioBuilder<Connecter> {
        Builder on(String interfaceId);

        Builder mode(TcpdumpMode mode);

        Builder rule(String rule);

        Builder bind(Address bindAddress);
    }

    private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(TcpdumpSocket.class.getPackage().getName());

    private static final String TCPDUMP_DEFAULT_INTERFACE_ID = CONFIG.getString("tcpdump.interface");
    private static final String TCPDUMP_DEFAULT_RULE = CONFIG.getString("tcpdump.rule");

    public static Builder builder() {
        return new Builder() {
            private String interfaceId = TCPDUMP_DEFAULT_INTERFACE_ID;
            private TcpdumpMode mode = TcpdumpMode.RAW;
            private String rule = TCPDUMP_DEFAULT_RULE;

            private Address bindAddress = null;

            @Override
            public Builder on(String interfaceId) {
                this.interfaceId = interfaceId;
                return this;
            }

            @Override
            public Builder mode(TcpdumpMode mode) {
                this.mode = mode;
                return this;
            }

            @Override
            public Builder rule(String rule) {
                this.rule = rule;
                return this;
            }

            @Override
            public Builder bind(Address bindAddress) {
                this.bindAddress = bindAddress;
                return this;
            }

            @Override
            public Connecter create(NinioProvider ninioProvider) {
                if (interfaceId == null) {
                    throw new NullPointerException("interfaceId");
                }
                return new TcpdumpSocket(interfaceId, mode, rule, bindAddress);
            }
        };
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpdumpSocket.class);

    private static final String TCPDUMP_COMMAND = CONFIG.getString("tcpdump.path");
    private static final int READ_BUFFER_SIZE = CONFIG.getBytes("tcpdump.datagram.read.size").intValue();
    private static final int WRITE_BUFFER_SIZE = CONFIG.getBytes("tcpdump.datagram.write.size").intValue();
    private static final String PORT_PLACEHOLDER = CONFIG.getString("tcpdump.port.placeholder");


    private static void execute(String name, Runnable runnable) {
        new ClassThreadFactory(TcpdumpSocket.class, name).newThread(runnable).start();
    }

    private final String interfaceId;
    private final TcpdumpMode mode;
    private final String rule;
    private final Address bindAddress;

    private DatagramSocket socket = null;
    private Process process = null;
    private boolean closed = false;

    private final RequestTracker inTracker;
    private final RequestTracker outTracker;

    private TcpdumpSocket(String interfaceId, TcpdumpMode mode, String rule, Address bindAddress) { //, final boolean promiscuous) {
        this.interfaceId = interfaceId;
        this.mode = mode;
        this.rule = rule;
        this.bindAddress = bindAddress;
        String prefix = "TCP_DUMP";
        inTracker = RequestTrackerManager.instance().getTracker(prefix, "in");
        outTracker = RequestTrackerManager.instance().getTracker(prefix, "out");
        DisplayableMetricsManager.instance().percent(outTracker, inTracker, prefix, "lost");
    }

    @Override
    public void connect(final Connection callback) {
        if (socket != null) {
            throw new IllegalStateException("connect() cannot be called twice");
        }

        if (closed) {
            callback.failed(new IOException("Closed"));
            return;
        }

        int bindPort;
        final DatagramSocket s;
        try {
            if (bindAddress == null) {
                s = new DatagramSocket();
            } else {
                s = new DatagramSocket(new InetSocketAddress(InetAddress.getByAddress(bindAddress.ip), bindAddress.port));
            }
            try {
                bindPort = s.getLocalPort();
                s.setReceiveBufferSize(READ_BUFFER_SIZE);
                s.setSendBufferSize(WRITE_BUFFER_SIZE);
                LOGGER.debug("Datagram socket created (bound to {}, port {}, receive buffer size = {}, send buffer size = {})", bindAddress, s.getLocalPort(), s.getReceiveBufferSize(), s.getSendBufferSize());
            } catch (IOException se) {
                s.close();
                throw se;
            }
        } catch (IOException ee) {
            closed = true;
            callback.failed(new IOException("Could not create send socket", ee));
            return;
        }

        //

        final TcpdumpReader tcpdumpReader = (mode == TcpdumpMode.RAW) ? new RawTcpdumpReader(interfaceId.equals("any")) : new HexTcpdumpReader();

        File dir = new File(".");

        List<String> toExec = new LinkedList<>();
        toExec.add("sudo");
        toExec.add(TCPDUMP_COMMAND);
        //%% toExec.add("-w");
        //%% toExec.add("-"); // Try with /dev/stdout
        toExec.add("-i");
        toExec.add(interfaceId);
        toExec.add("-nn");
        for (String o : tcpdumpReader.tcpdumpOptions()) {
            toExec.add(o);
        }
        toExec.add("-K");
        // if (!promiscuous) {
        toExec.add("-p");
        // }
        toExec.add("-q");
        toExec.add("-s");
        toExec.add("0");
        // toExec.add("-U"); // Unbuffers output
        if (rule != null) {
            for (String p : Splitter.on(' ').split(rule)) {
                String r = p.trim();
                if (!r.isEmpty()) {
                    r = r.replace(PORT_PLACEHOLDER, Integer.toString(bindPort));
                    toExec.add(r);
                }
            }
        }

        ProcessBuilder pb = new ProcessBuilder(toExec);
        pb.directory(dir);
        final Process p;
        try {
            LOGGER.info("In: {}, executing: {}", dir.getCanonicalPath(), Joiner.on(' ').join(toExec));
            p = pb.start();
        } catch (IOException ee) {
            s.close();
            closed = true;
            callback.failed(new IOException("Could not create process", ee));
            return;
        }

        socket = s;
        process = p;

        callback.connected(null);

        final InputStream error = process.getErrorStream();
        execute("err", () -> {
            try {
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(error));
                    while (true) {
                        String line = r.readLine();
                        if (line == null) {
                            break;
                        }
                        LOGGER.debug("Tcpdump message: {}", line);
                    }
                } finally {
                    error.close();
                }
            } catch (IOException e) {
                LOGGER.trace("Error in tcpdump process", e);
            }
        });

        final InputStream input = process.getInputStream();
        execute("in", () -> {
            try {
                try {
                    tcpdumpReader.read(input, new TcpdumpReader.Handler() {
                        @Override
                        public void handle(double timestamp, Address sourceAddress, Address destinationAddress, ByteBuffer buffer) {
                            inTracker.track(Address.ipToString(sourceAddress.ip), addr ->
                                    String.format("Received ping from %s ", addr));

                            callback.received(sourceAddress, buffer);
                        }
                    });
                } finally {
                    input.close();
                }
            } catch (IOException e) {
                LOGGER.trace("Error in tcpdump process", e);
            }
        });

        execute("eat", () -> {
            byte[] buffer = new byte[64 * 1024];
            try {
                while (true) {
                    if (s.isClosed()) {
                        break;
                    }
                    s.receive(new DatagramPacket(buffer, buffer.length));
                }
            } catch (IOException e) {
                LOGGER.trace("Error eating UDP packets", e);
            }
        });

        execute("wait", () -> {
            int code;
            try {
                code = p.waitFor();
            } catch (InterruptedException e) {
                code = -1;
            }

            try {
                error.close();
            } catch (IOException e) {
            }
            try {
                input.close();
            } catch (IOException e) {
            }

            p.destroy();
            s.close();

            if (code != 0) {
                callback.failed(new IOException("Non zero return code from tcpdump: " + code));
            } else {
                callback.closed();
            }
        });
    }

    @Override
    public void send(Address address, ByteBuffer buffer, final SendCallback callback) {
        if (socket == null) {
            throw new IllegalStateException("send() must be called after connect()");
        }

        LOGGER.trace("Sending datagram to: {}", address);

        try {
            if (closed) {
                throw new IOException("Closed");
            }

            if (buffer != null) {
                DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining(), InetAddress.getByAddress(address.ip), address.port);
                socket.send(packet);
                outTracker.track(Address.ipToString(address.ip), addr -> String.format("Sending tcpdump to %s ", addr));
            }

            callback.sent();
        } catch (Exception e) {
            callback.failed(new IOException("Could not write", e));
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        if (socket != null) {
            socket.close();
        }
        if (process != null) {
            process.destroy();
        }
    }
}
