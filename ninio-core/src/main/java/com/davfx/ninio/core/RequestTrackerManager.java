package com.davfx.ninio.core;

import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private static final Path TRACK_PATH = Paths.get(CONFIG.getString("supervision.tracking.path"));

    private static RequestTrackerManager INSTANCE = null;

    private final Map<String, RequestTracker> requestTrackers = new ConcurrentSkipListMap<>();
    private final Map<String, Relation> relations = new ConcurrentSkipListMap<>();

    private Instant lastTrackingFileUpdate = Instant.EPOCH;

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

        executor.scheduleAtFixedRate(() -> display(false),
                displayStart, SUPERVISION_DISPLAY.toMillis(), TimeUnit.MILLISECONDS);

        executor.scheduleAtFixedRate(() -> {
            LOGGER.debug("Clear Trackers");
            display(true);
            requestTrackers.values().forEach(RequestTracker::reset);
        }, clearStart, SUPERVISION_CLEAR.toMillis(), TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(this::loadTrackingDevices, 2, 5, TimeUnit.SECONDS);
    }

    private void display(boolean clear) {
        if (LOGGER.isDebugEnabled() || (clear && LOGGER.isInfoEnabled())) {
            String prefix = clear ? "[clear] " : "";
            ImmutableMap<String, Long> counts = requestTrackers.entrySet().stream()
                    .map(entry -> Maps.immutableEntry(entry.getKey(), entry.getValue().count()))
                    .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                if (entry.getValue() != 0) {
                    String message = String.format("%s%s = %s", prefix, entry.getKey(), entry.getValue());
                    if(clear) LOGGER.info(message);
                    else LOGGER.debug(message);
                }
            }
            for (Map.Entry<String, Relation> entry : relations.entrySet()) {
                String relation = entry.getValue().compute();
                if (relation != null) {
                    String message = String.format("%s%s = %s", prefix, entry.getKey(), relation);
                    if(clear) LOGGER.info(message);
                    else LOGGER.debug(message);
                }
            }
        }
    }

    public RequestTracker getTracker(String trackerName, String... tags) {
        return requestTrackers.computeIfAbsent(key(trackerName, tags), RequestTracker::new);
    }

    public interface Relation {
        String compute();
    }

    private void loadTrackingDevices() {
        try {
            if (Files.exists(TRACK_PATH)) {
                Instant fileLastModifiedTime = Files.getLastModifiedTime(TRACK_PATH).toInstant();
                if (fileLastModifiedTime.isAfter(lastTrackingFileUpdate)) {
                    lastTrackingFileUpdate = fileLastModifiedTime;
                    ImmutableSet<String> addressToFollow = ImmutableSet.copyOf(Files.readAllLines(TRACK_PATH));
                    LOGGER.info("Will now follow {} devices", addressToFollow.size());
                    LOGGER.debug("Will now follow devices {}", addressToFollow);
                    requestTrackers.values().forEach(requestTracker -> requestTracker.setAddressToFollow(addressToFollow));
                } else {
                    LOGGER.trace("No changes of file {}", TRACK_PATH);
                }
            } else {
                if (lastTrackingFileUpdate.isAfter(Instant.EPOCH)) {
                    LOGGER.trace("File {} has been deleted, unfollowing all devices", TRACK_PATH);
                    lastTrackingFileUpdate = Instant.EPOCH;
                    requestTrackers.values().forEach(requestTracker -> requestTracker.setAddressToFollow(ImmutableSet.of()));
                } else {
                    LOGGER.trace("File {} does no exist", TRACK_PATH);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error while loading tracking devices", e);
        }
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
                .map(String::toUpperCase)
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
