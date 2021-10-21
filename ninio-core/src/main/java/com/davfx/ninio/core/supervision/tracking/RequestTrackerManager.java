package com.davfx.ninio.core.supervision.tracking;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.core.supervision.metrics.DisplayableMetricsManager;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class RequestTrackerManager implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestTrackerManager.class);
    private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(Address.class.getPackage().getName());
    private static final Path TRACK_PATH = Paths.get(CONFIG.getString("supervision.tracking.path"));

    private static RequestTrackerManager INSTANCE = null;

    private final Map<String, RequestTracker> requestTrackers = new ConcurrentSkipListMap<>();
    private final AtomicReference<ImmutableSet<String>> addressesToFollow = new AtomicReference<>(ImmutableSet.of());

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

    private RequestTrackerManager() {
        executor.scheduleAtFixedRate(this::loadTrackingDevices, 2, 5, TimeUnit.SECONDS);
    }

    public RequestTracker getTracker(String... tags) {
        return requestTrackers.computeIfAbsent(DisplayableMetricsManager.key(tags), name -> {
            RequestTracker requestTracker = new RequestTracker(name);
            requestTracker.setAddressToFollow(addressesToFollow.get());
            return DisplayableMetricsManager.instance().tracker(requestTracker);
        });
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
                    setAddressToFollow(addressToFollow);
                } else {
                    LOGGER.trace("No changes of file {}", TRACK_PATH);
                }
            } else {
                if (lastTrackingFileUpdate.isAfter(Instant.EPOCH)) {
                    LOGGER.trace("File {} has been deleted, unfollowing all devices", TRACK_PATH);
                    lastTrackingFileUpdate = Instant.EPOCH;
                    setAddressToFollow(ImmutableSet.of());
                } else {
                    LOGGER.trace("File {} does no exist", TRACK_PATH);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error while loading tracking devices", e);
        }
    }

    private void setAddressToFollow(ImmutableSet<String> addressesToFollow) {
        this.addressesToFollow.set(addressesToFollow);
        requestTrackers.values().forEach(requestTracker -> requestTracker.setAddressToFollow(addressesToFollow));
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
