/*
 * PmtMetrics.java
 * Created on May 06, 2026, 3:32 PM
 *
 * Copyright 2026 BlueCat Networks (USA) Inc. and its affiliates and licensors. All Rights Reserved.
 *
 * BlueCat Networks and its licensors hereby assert and retain all rights, title, and interest in and to the code,
 * including any and all modifications, enhancements, or derivative works thereof (collectively, the "Code"). This
 * includes, but is not limited to, all intellectual property rights, whether registered or unregistered, associated
 * with the Code. The Code contains trade secrets and proprietary and confidential information of BlueCat Networks
 * and its licensors. It is protected under applicable worldwide copyright and trade secret laws. No rights, title,
 * or interest in the Code are transferred to any third party without the explicit written consent of BlueCat
 * Networks. Any unauthorized use, reproduction, or distribution of the Code is strictly prohibited and may result in
 * legal action.
 */
package com.davfx.ninio.core.supervision.metrics.pmt;

import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.typesafe.config.Config;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.core.metrics.Info;
import io.prometheus.metrics.core.metrics.Summary;
import io.prometheus.metrics.model.registry.Collector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 *
 *
 * @author Baptiste Le Bail
 */
public class PmtMetricsImpl implements PmtMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(PmtMetricsImpl.class);

    private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(PmtMetricsImpl.class.getPackage().getName());
    private static final Duration RECURRING_METRIC_UPDATE_RATE = Duration.ofMillis((long) ConfigUtils.getDuration(CONFIG, "updateRate") * 1000);

    private static final Set<Runnable> RECURRING_METRIC_UPDATES = ConcurrentHashMap.newKeySet();
    private static final PrometheusRegistry REGISTRY = PrometheusRegistry.defaultRegistry;

    private static final PmtMetricsImpl INSTANCE = new PmtMetricsImpl();

    public static PmtMetrics get() {
        return INSTANCE;
    }

    private PmtMetricsImpl() {
        scheduleRecurringMetrics();
    }

    private void scheduleRecurringMetrics() {
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("recurring-metrics").build();
        LOGGER.debug("Recurring metrics rate = {}", RECURRING_METRIC_UPDATE_RATE);
        try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory)) {
            scheduler.scheduleAtFixedRate(this::updateRecurringMetrics, 0, RECURRING_METRIC_UPDATE_RATE.getSeconds(), TimeUnit.SECONDS);
        }
    }

    private void updateRecurringMetrics() {
        LOGGER.debug("Updating {} recurring metrics", RECURRING_METRIC_UPDATES.size());
        for (Runnable metricUpdate : RECURRING_METRIC_UPDATES) {
            metricUpdate.run();
        }
    }

    @Override
    public PrometheusRegistry registry() {
        return REGISTRY;
    }

    @Override
    public Counter counter(String name, String description, String... labelNames) {
        return Counter.builder()
                .name(name)
                .help(description)
                .labelNames(labelNames)
                .register();
    }

    @Override
    public MaxCounter maxCounter(String name, String description, String... labelNames) {
        return new MaxCounter(registry(), name, description, labelNames);
    }

    @Override
    public StaticPercent percent(String name, String description, long total, String... labelNames) {
        return new StaticPercent(registry(), name, description, total, labelNames);
    }

    @Override
    public DynamicPercent dynamicPercent(String name, String description, Gauge gaugeA, Gauge gaugeB) {
        return new DynamicPercent(registry(), name, description, gaugeA, gaugeB);
    }

    @Override
    public Gauge gauge(String name, String description, String... labelNames) {
        return Gauge.builder()
                .name(name)
                .help(description)
                .labelNames(labelNames)
                .register();
    }

    @Override
    public Histogram histogram(String name, String description, String... labelNames) {
        return Histogram.builder()
                .name(name)
                .help(description)
                .unit(Unit.SECONDS)
                .labelNames(labelNames)
                .register();
    }

    @Override
    public Summary summary(String name, String description, ImmutableSet<Double> quantiles, String... labelNames) {
        Summary.Builder summaryBuilder = Summary.builder()
                .name(name)
                .help(description)
                .labelNames(labelNames);

        quantiles.forEach(summaryBuilder::quantile);

        return summaryBuilder.register();
    }

    @Override
    public Info info(String name, String description, String... labelNames) {
        return Info.builder()
                .name(name)
                .help(description)
                .labelNames(labelNames)
                .register();
    }

    @Override
    public void recurring(Runnable runnable) {
        RECURRING_METRIC_UPDATES.add(runnable);
    }

    @Override
    public void unregister(Collector metric) {
        REGISTRY.unregister(metric);
    }

    @Override
    public void unregister(GaugeBasedMetric metric) {
        unregister(metric.get());
    }
}