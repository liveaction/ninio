package com.davfx.ninio.core;

import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class RequestTrackerManager implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestTrackerManager.class);
    private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(RequestTrackerManager.class.getPackage().getName());
    private static final Duration SUPERVISION_DISPLAY = Duration.ofMillis((long) ConfigUtils.getDuration(CONFIG, "supervision.display") * 1000);
    private static final Duration SUPERVISION_CLEAR = Duration.ofMillis((long) ConfigUtils.getDuration(CONFIG, "supervision.clear") * 1000);

    private static RequestTrackerManager INSTANCE = null;

    private final Map<String, RequestTracker> requestTrackers = new ConcurrentSkipListMap<>();
    private final Map<String, Relation> relations = new ConcurrentSkipListMap<>();

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("simple-metrics")
            .setUncaughtExceptionHandler((t, e) -> LOGGER.error("Uncaught error in thread {}", t, e))
            .build());

    public static RequestTrackerManager instance() {
        if (INSTANCE == null) {
            INSTANCE = new RequestTrackerManager();
        }
        return INSTANCE;
    }

    public RequestTrackerManager() {
        Instant now = Instant.now();
        long clearStart = SUPERVISION_CLEAR.toMillis() - (now.toEpochMilli() % SUPERVISION_CLEAR.toMillis());
        long displayStart = SUPERVISION_DISPLAY.toMillis() - (now.toEpochMilli() % SUPERVISION_DISPLAY.toMillis());

        executor.scheduleAtFixedRate(this::display,
                displayStart, SUPERVISION_DISPLAY.toMillis(), TimeUnit.MILLISECONDS);

        executor.scheduleAtFixedRate(() -> {
            LOGGER.warn("Clear Trackers");
            display();
            requestTrackers.values().forEach(RequestTracker::reset);
        }, clearStart, SUPERVISION_CLEAR.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void display() {
        ImmutableMap<String, Long> counts = requestTrackers.entrySet().stream()
                .map(entry -> Maps.immutableEntry(entry.getKey(), entry.getValue().count()))
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (entry.getValue() != 0) {
                LOGGER.warn("{} = {}", entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Relation> entry : relations.entrySet()) {
            String relation = entry.getValue().compute();
            if (relation != null) {
                LOGGER.warn("{} = {}", entry.getKey(), relation);
            }
        }
    }

    public RequestTracker getTracker(String trackerName, String... tags) {
        return requestTrackers.computeIfAbsent(key(trackerName, tags), k -> new RequestTracker(trackerName));
    }

    public static interface Relation {
        String compute();
    }

    // (a - b) / a
    public static final class PercentRelation implements Relation {
        public final RequestTracker trackerA;
        public final RequestTracker trackerB;

        public PercentRelation(RequestTracker trackerA, RequestTracker trackerB) {
            this.trackerA = trackerA;
            this.trackerB = trackerB;
        }

        @Override
        public String compute() {
            long av = trackerA.count();
            long bv = trackerB.count();
            if (av == 0 && bv == 0) {
                return null;
            }
            return percent(av, bv);
        }
    }

    private static String percent(long out, long in) {
        return String.format("%.2f", ((double) (out - in)) * 100d / ((double) out)) + "%";
    }

    public RequestTrackerManager relation(String key, Relation relation, String... prefix) {
        relations.put(key(key, prefix), relation);
        return this;
    }

    private static String key(String key, String... prefix) {
        return Stream.concat(
                Arrays.stream(prefix)
                        .map(RequestTrackerManager::wrap),
                Stream.of(key))
                .reduce((s1, s2) -> s1 + " " + s2)
                .orElse("");
    }

    private static String wrap(String val) {
        return '[' + val + ']';
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
