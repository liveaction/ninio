package com.davfx.ninio.core.supervision.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.core.supervision.tracking.RequestTracker;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class aims to regularly display metrics in a centralized way.
 * <p>
 * It allows both dropwizard metrics (with Slf4jReporter), and custom metrics which are reset every 5mn (with custom reporting)
 */
public class DisplayableMetricsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisplayableMetricsManager.class);

    private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(Address.class.getPackage().getName());
    private static final Duration SUPERVISION_DISPLAY = Duration.ofMillis((long) ConfigUtils.getDuration(CONFIG, "supervision.metrics.display") * 1000);
    private static final Duration SUPERVISION_CLEAR = Duration.ofMillis((long) ConfigUtils.getDuration(CONFIG, "supervision.metrics.clear") * 1000);
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("simple-metrics")
            .setUncaughtExceptionHandler((t, e) -> LOGGER.error("Uncaught error in thread {}", t, e))
            .build());
    private final MetricRegistry metricRegistry;

    private final ConcurrentMap<String, Metric> metricsByName = Maps.newConcurrentMap();
    private static final DisplayableMetricsManager INSTANCE = new DisplayableMetricsManager();

    public static DisplayableMetricsManager instance() {
        return INSTANCE;
    }

    private DisplayableMetricsManager() {
        // initialization for dropwizard metrics
        this.metricRegistry = new MetricRegistry();
        Slf4jReporter.forRegistry(metricRegistry)
                .outputTo(LOGGER)
                .markWith(MarkerFactory.getMarker("METRICS"))
                .build()
                .start(5, TimeUnit.MINUTES);

        // schedule all custom displays
        Instant now = Instant.now();
        long clearStart = SUPERVISION_CLEAR.toMillis() - (now.toEpochMilli() % SUPERVISION_CLEAR.toMillis());
        long displayStart = SUPERVISION_DISPLAY.toMillis() - (now.toEpochMilli() % SUPERVISION_DISPLAY.toMillis());

        executor.scheduleAtFixedRate(() -> display(false),
                displayStart, SUPERVISION_DISPLAY.toMillis(), TimeUnit.MILLISECONDS);

        executor.scheduleAtFixedRate(() -> {
            try {
                display(true);
                LOGGER.debug("Clear Trackers");
                metricsByName.values().forEach(Metric::reset);
            } catch (Throwable t) {
                LOGGER.info("Error happened while clearing trackers", t);
            }
        }, clearStart, SUPERVISION_CLEAR.toMillis(), TimeUnit.MILLISECONDS);
    }

    public <T extends Metric> T addIfAbsent(T m) {
        Metric metric = metricsByName.putIfAbsent(m.name(), m);
        if (metric == null) {
            return m;
        }
        try {
            T finalValue = (T) metric;
            LOGGER.trace("Metric {} already existed, it will not be replaced", m.name());
            return finalValue;
        } catch (ClassCastException e) {
            LOGGER.warn("Metric {} is defined as {}, trying to add it as {}, probably an unexpected name conflict (previous metric will stay, new one will be ignored)", m.name(), metric.getClass().getSimpleName(), m.getClass().getSimpleName());
            throw e;
        }
    }

    public CounterMetric counter(String... tags) {
        return addIfAbsent(new CounterMetric(key(tags)));
    }

    public MaxCounterMetric maxCounter(String... tags) {
        return addIfAbsent(new MaxCounterMetric(key(tags)));
    }

    public TimerMetric timer(String... tags) {
        return addIfAbsent(new TimerMetric(key(tags)));
    }

    public RequestTracker tracker(RequestTracker tracker) {
        return addIfAbsent(tracker);
    }

    public PercentMetric percent(LongMetric trackerA, LongMetric trackerB, String... tags) {
        return addIfAbsent(new PercentMetric(trackerA, trackerB, key(tags)));
    }

    private void display(boolean clear) {
        if (LOGGER.isDebugEnabled() || (clear && LOGGER.isInfoEnabled())) {
            String prefix = clear ? "[clear] " : "";
            for (Map.Entry<String, Metric> entry : metricsByName.entrySet()) {
                if (entry.getValue().getValue() != null) {
                    String message = String.format("%s%s = %s", prefix, entry.getKey(), entry.getValue().getValue());
                    if (clear) LOGGER.info(message);
                    else LOGGER.debug(message);
                }
            }
        }
    }

    public static String key(String... prefix) {
        return Arrays.stream(prefix)
                .map(String::toUpperCase)
                .map(Metric::wrapTag)
                .reduce((s1, s2) -> s1 + " " + s2)
                .orElse("");
    }

    public static MetricRegistry getMetricRegistry() {
        return INSTANCE.metricRegistry;
    }
}
