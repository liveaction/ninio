package com.davfx.ninio.core.supervision.metrics;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.core.supervision.tracking.RequestTracker;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.stream.Collectors.toMap;

/**
 * This class aims to regularly display metrics in a centralized way.
 * <p>
 * It allows both dropwizard metrics (with Slf4jReporter), and custom metrics which are reset every 5mn (with custom reporting)
 */
public class DisplayableMetricsManager {
    public static final String METRICS_TAG = "[app-metrics]";

    private static final Logger LOGGER = LoggerFactory.getLogger(DisplayableMetricsManager.class);

    private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(Address.class.getPackage().getName());
    private static final Duration SUPERVISION_DISPLAY = Duration.ofMillis((long) ConfigUtils.getDuration(CONFIG, "supervision.metrics.display") * 1000);
    private static final Duration SUPERVISION_CLEAR = Duration.ofMillis((long) ConfigUtils.getDuration(CONFIG, "supervision.metrics.clear") * 1000);
    private static final DisplayableMetricsManager INSTANCE = new DisplayableMetricsManager();

    private final ConcurrentMap<String, Map.Entry<Metric, MetricsParams>> metricsByName = Maps.newConcurrentMap();

    public static DisplayableMetricsManager instance() {
        return INSTANCE;
    }

    private DisplayableMetricsManager() {
        // schedule all custom displays
        Instant now = Instant.now();
        long displayStart = 1 + SUPERVISION_DISPLAY.toMillis() - (now.toEpochMilli() % SUPERVISION_DISPLAY.toMillis());

        AtomicLong previousDisplaySlot = new AtomicLong();

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("simple-metrics")
                .setUncaughtExceptionHandler((t, e) -> LOGGER.error("Uncaught error in thread {}", t, e))
                .build());

        executor.scheduleAtFixedRate(() -> {
                    try {
                        long currentSlot = Instant.now().toEpochMilli() / SUPERVISION_CLEAR.toMillis();
                        long previousSlot = previousDisplaySlot.getAndSet(currentSlot);
                        boolean clear = previousSlot != 0 && currentSlot != previousSlot;

                        display(clear);

                        if (clear) {
                            LOGGER.debug("Clear metrics");
                            clear();
                        }
                    } catch (Throwable t) {
                        LOGGER.warn("Error happened while displaying metrics", t);
                    }
                },
                displayStart, SUPERVISION_DISPLAY.toMillis(), TimeUnit.MILLISECONDS);
    }

    @VisibleForTesting
    void clear() {
        Set<Metric> toDispose = new HashSet<>();
        Set<Metric> toReset = new HashSet<>();

        metricsByName.values().forEach(paramsByMetric -> {
            Metric metric = paramsByMetric.getKey();
            MetricsParams params = paramsByMetric.getValue();

            if (params.isDisposable()) {
                toDispose.add(metric);
            } else {
                toReset.add(metric);
            }
        });

        toDispose.forEach(metric -> remove(metric.name()));
        toReset.forEach(Metric::reset);
    }

    public <T extends Metric> T addIfAbsent(T metric) {
        return addIfAbsent(metric, new MetricsParams(metric.name()));
    }

    public <T extends Metric> T addIfAbsent(T metric, MetricsParams params) {
        String metricName = metric.name();
        Map.Entry<Metric, MetricsParams> paramsByMetric = metricsByName.putIfAbsent(metricName, Maps.immutableEntry(metric, params));

        if (paramsByMetric == null) {
            return metric;
        }

        try {
            T finalValue = (T) paramsByMetric.getKey();
            LOGGER.trace("Metric {} already existed, it will not be replaced", metricName);
            return finalValue;
        } catch (ClassCastException e) {
            LOGGER.warn("Metric {} is defined as {}, trying to add it as {}, probably an unexpected name conflict (previous metric will stay, new one will be ignored)",
                    metricName, metric.getClass().getSimpleName(), metric.getClass().getSimpleName());
            throw e;
        }
    }

    public void remove(String metricName) {
        metricsByName.remove(metricName);
    }

    public CounterMetric counter(String... tags) {
        return counter(new MetricsParams(tags));
    }

    public CounterMetric counter(MetricsParams params) {
        return addIfAbsent(new CounterMetric(key(params.tags())), params);
    }

    public MaxCounterMetric maxCounter(String... tags) {
        return maxCounter(new MetricsParams(tags));
    }

    public MaxCounterMetric maxCounter(MetricsParams params) {
        return addIfAbsent(new MaxCounterMetric(key(params.tags())), params);
    }

    public TimerMetric timer(String... tags) {
        return timer(new MetricsParams(tags));
    }

    public TimerMetric timer(MetricsParams params) {
        return addIfAbsent(new TimerMetric(key(params.tags())), params);
    }

    public RequestTracker tracker(RequestTracker tracker) {
        return addIfAbsent(tracker, new MetricsParams());
    }

    public PercentMetric percent(LongMetric trackerA, LongMetric trackerB, String... tags) {
        return percent(trackerA, trackerB, new MetricsParams(tags));
    }

    public PercentMetric percent(LongMetric trackerA, LongMetric trackerB, MetricsParams params) {
        return addIfAbsent(new PercentMetric(trackerA, trackerB, key(params.tags())), params);
    }

    public PercentMetric percent(long staticValue, LongMetric trackerB, String... tags) {
        return percent(staticValue, trackerB, new MetricsParams(tags));
    }

    public PercentMetric percent(long staticValue, LongMetric trackerB, MetricsParams params) {
        return addIfAbsent(new PercentMetric(new StaticCounter(staticValue), trackerB, key(params.tags())), params);
    }

    private void display(boolean clear) {
        if (LOGGER.isDebugEnabled() || (clear && LOGGER.isInfoEnabled())) {
            String prefix = clear ? METRICS_TAG + " [clear] " : METRICS_TAG + " ";

            for (Map.Entry<String, Metric> entry : getMetrics().entrySet()) {
                if (entry.getValue().getValue() != null) {
                    String message = String.format("%s%s = %s", prefix, entry.getKey(), entry.getValue().getValue());
                    if (clear) LOGGER.info(message);
                    else LOGGER.debug(message);
                }
            }
        }
    }

    public static String key(Collection<String> prefix) {
        return prefix.stream()
                .map(String::toUpperCase)
                .map(Metric::wrapTag)
                .reduce((s1, s2) -> s1 + " " + s2)
                .orElse("");
    }

    public Map<String, Metric> getMetrics() {
        return Collections.unmodifiableMap(metricsByName.entrySet()
                .stream()
                .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().getKey())));
    }
}
