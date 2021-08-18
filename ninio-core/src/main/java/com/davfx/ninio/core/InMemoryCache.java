package com.davfx.ninio.core;

import com.davfx.ninio.core.supervision.metrics.DisplayableMetricsManager;
import com.davfx.ninio.core.supervision.tracking.RequestTracker;
import com.davfx.ninio.core.supervision.tracking.RequestTrackerManager;
import com.davfx.ninio.util.DateUtils;
import com.davfx.ninio.util.MemoryCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public final class InMemoryCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCache.class);

    public interface Builder<T> extends NinioBuilder<Connecter> {
        Builder<T> dataExpiration(double dataExpiration);

        Builder<T> requestExpiration(double requestExpiration);

        Builder<T> using(Interpreter<T> interpreter);

        Builder<T> name(String name);

        Builder<T> with(NinioBuilder<Connecter> builder);
    }

    public static <T> Builder<T> builder() {
        return new Builder<T>() {
            private NinioBuilder<Connecter> builder = UdpSocket.builder();

            private String name = "";
            private double dataExpiration = 0d;
            private double requestExpiration = 0d;
            private Interpreter<T> interpreter = null;

            @Override
            public Builder<T> using(Interpreter<T> interpreter) {
                this.interpreter = interpreter;
                return this;
            }

            @Override
            public Builder<T> name(String name) {
                this.name = name;
                return this;
            }

            @Override
            public Builder<T> with(NinioBuilder<Connecter> builder) {
                this.builder = builder;
                return this;
            }

            @Override
            public Builder<T> dataExpiration(double dataExpiration) {
                this.dataExpiration = dataExpiration;
                return this;
            }

            @Override
            public Builder<T> requestExpiration(double requestExpiration) {
                this.requestExpiration = requestExpiration;
                return this;
            }

            @Override
            public Connecter create(NinioProvider ninioProvider) {
                if (builder == null) {
                    throw new NullPointerException("builder");
                }
                if (interpreter == null) {
                    throw new NullPointerException("interpreter");
                }

                return new InnerConnecter<>(name, dataExpiration, requestExpiration, interpreter, builder.create(ninioProvider));
            }
        };
    }

    private static final class InnerConnecter<T> implements Connecter {
        private final Connecter wrappee;
        private final Interpreter<T> interpreter;
        private final double dataExpiration;
        private final double requestExpiration;
        private final MemoryCache<Address, CacheByAddress<T>> cacheByDestinationAddress;
        private final RequestTracker cacheOutputCounter;
        private final RequestTracker cacheInputCounter;
        private Connection connectCallback = null;

        public InnerConnecter(String name, double dataExpiration, double requestExpiration, Interpreter<T> interpreter, Connecter wrappee) {
            String prefix = name + "_cache";
            this.cacheOutputCounter = RequestTrackerManager.instance().getTracker("out", prefix);
            this.cacheInputCounter = RequestTrackerManager.instance().getTracker("in", prefix);
            DisplayableMetricsManager.instance().percent(cacheOutputCounter, cacheInputCounter, "lost", prefix);
            this.dataExpiration = dataExpiration;
            this.requestExpiration = Math.min(dataExpiration, requestExpiration);
            this.interpreter = interpreter;
            this.wrappee = wrappee;

            cacheByDestinationAddress = MemoryCache.<Address, CacheByAddress<T>>builder().expireAfterAccess(dataExpiration).build();
        }

        @Override
        public void connect(final Connection callback) {
            synchronized (cacheByDestinationAddress) {
                connectCallback = callback;
            }

            wrappee.connect(new Connection() {
                @Override
                public void received(Address address, ByteBuffer sourceBuffer) {
                    T sub;
                    {
                        ByteBuffer sb = sourceBuffer.duplicate();
                        try {
                            sub = interpreter.handleResponse(sb);
                        } catch (Exception e) {
                            LOGGER.trace("Invalid response packet", e);
                            return;
                        }
                        if (sub == null) {
                            callback.received(address, sourceBuffer);
                            return;
                        }
                    }

                    String key;
                    List<T> to;
                    synchronized (cacheByDestinationAddress) {
                        CacheByAddress<T> cache = cacheByDestinationAddress.get(address);
                        if (cache == null) {
                            LOGGER.trace("No cache (address = {})", address);
                            return;
                        }

                        key = cache.subToKey.get(sub);
                        if (key == null) {
                            LOGGER.trace("No key (address = {}, sub = {}) - {}", address, sub, cache.subToKey);
                            return;
                        }

                        cache.subToKey.remove(sub);

                        DataCache<T> subs = cache.requestsByKey.get(key);
                        if (subs == null) {
                            LOGGER.trace("No corresponding subs (address = {}, sub = {}, key = {})", address, sub, key);
                            return;
                        }

                        to = new LinkedList<>();
                        for (T k : subs.subs.keys()) {
                            to.add(k);
                        }
                        subs.subs.clear();

                        subs.data = sourceBuffer.duplicate();
                    }

                    for (T s : to) {
                        ByteBuffer ssb = sourceBuffer.duplicate();
                        ByteBuffer b;
                        if (sub.equals(s)) {
                            b = ssb;
                        } else {
                            try {
                                b = interpreter.transform(ssb, s);
                            } catch (Exception e) {
                                LOGGER.trace("Invalid response packet", e);
                                continue;
                            }
                        }
                        if (b != null) {
                            callback.received(address, b);
                        }
                    }

                    LOGGER.trace("New response (address = {}, sub = {}, key = {})", address, sub, key);

                }

                @Override
                public void connected(Address address) {
                    callback.connected(address);
                }

                @Override
                public void failed(IOException ioe) {

                    callback.failed(ioe);
                }

                @Override
                public void closed() {

                    callback.closed();
                }
            });
        }

        @Override
        public void send(Address address, ByteBuffer sourceBuffer, SendCallback sendCallback) {

            Context<T> context;
            {
                ByteBuffer sb = sourceBuffer.duplicate();
                try {
                    context = interpreter.handleRequest(sb);
                } catch (Exception e) {
                    sendCallback.failed(new IOException("Invalid packet", e));
                    return;
                }
                if (context == null) {
                    wrappee.send(address, sourceBuffer, sendCallback);
                    return;
                }
            }

            double now = DateUtils.now();

            boolean send;
            Connection callback;
            ByteBuffer data;
            synchronized (cacheByDestinationAddress) {
                callback = connectCallback;

                CacheByAddress<T> cache = cacheByDestinationAddress.get(address);
                if (cache == null) {
                    LOGGER.trace("New cache (address = {}, expiration = {})", address, dataExpiration);
                    cache = new CacheByAddress<>(dataExpiration, requestExpiration);
                    cacheByDestinationAddress.put(address, cache);
                }

                DataCache<T> subs = cache.requestsByKey.get(context.key);

                if (subs != null) {
                    if (subs.data == null) {
                        if (now >= (subs.creation + requestExpiration)) {
                            subs = null;
                        }
                    }
                }

                if (subs == null) {
                    subs = new DataCache<>(now, requestExpiration);
                    cache.requestsByKey.put(context.key, subs);
                    send = true;
                    LOGGER.trace("New request (address = {}, key = {}, sub = {}) - {}", address, context.key, context.sub, cache.subToKey);
                } else {
                    send = false;
                    LOGGER.trace("Request already sent (address = {}, key = {}, sub = {}) - {}", address, context.key, context.sub, cache.subToKey);
                    cacheOutputCounter.track(Address.ipToString(address.ip), addr ->
                            String.format("Request cached (address = %s, key = %s, sub = %s)", addr, context.key, context.sub));
                }

                data = subs.data;

                if (send || (data == null)) {
                    subs.subs.put(context.sub, null);
                    cache.subToKey.put(context.sub, context.key);
                }
            }

            if (send) {
                wrappee.send(address, sourceBuffer, sendCallback);
            } else {
                if (callback != null) {
                    if (data != null) {
                        ByteBuffer b = data.duplicate();
                        ByteBuffer tb;
                        try {
                            tb = interpreter.transform(b, context.sub);
                        } catch (Exception e) {
                            sendCallback.failed(new IOException("Invalid packet", e));
                            return;
                        }
                        sendCallback.sent();
                        LOGGER.trace("Got from cache (address = {}, key = {}, sub = {})", address, context.key, context.sub);
                        cacheInputCounter.track(Address.ipToString(address.ip), addr ->
                                String.format("Response cached (address = %s, key = %s, sub = %s)", addr, context.key, context.sub));
                        callback.received(address, tb);
                        return;
                    }
                }

                LOGGER.trace("Response does not exist yet (address = {}, key = {}, sub = {})", address, context.key, context.sub);
                sendCallback.sent();
            }

        }

        @Override
        public void close() {
            wrappee.close();
        }
    }

    public static final class Context<T> {
        public final String key;
        public final T sub;

        public Context(String key, T sub) {
            this.key = key;
            this.sub = sub;
        }
    }

    public interface Interpreter<T> {
        Context<T> handleRequest(ByteBuffer packet);

        T handleResponse(ByteBuffer packet);

        ByteBuffer transform(ByteBuffer packet, T sub);
    }

    private static final class DataCache<T> {
        public final double creation;
        public ByteBuffer data = null;
        public final MemoryCache<T, Void> subs;

        public DataCache(double now, double requestExpiration) {
            creation = now;
            subs = MemoryCache.<T, Void>builder().expireAfterWrite(requestExpiration).build();
        }
    }

    private static final class CacheByAddress<T> {
        public final MemoryCache<String, DataCache<T>> requestsByKey;
        public final MemoryCache<T, String> subToKey;

        public CacheByAddress(double dataExpiration, double requestExpiration) {
            requestsByKey = MemoryCache.<String, DataCache<T>>builder().expireAfterWrite(dataExpiration).build();
            subToKey = MemoryCache.<T, String>builder().expireAfterWrite(requestExpiration).build();
        }
    }
}
