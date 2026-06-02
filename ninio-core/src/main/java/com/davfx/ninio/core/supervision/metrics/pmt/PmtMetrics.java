/*
 * PmtMetrics.java
 * Created on May 06, 2026, 3:33 PM
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

import com.google.common.collect.ImmutableSet;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.core.metrics.Info;
import io.prometheus.metrics.core.metrics.Summary;
import io.prometheus.metrics.model.registry.Collector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

/**
 *
 *
 * @author Baptiste Le Bail
 */
public interface PmtMetrics {

    ImmutableSet<Double> DEFAULT_QUANTILES = ImmutableSet.of(0.99, 0.95, 0.5, 0.15, 0.1);

    PrometheusRegistry registry();

    Counter counter(String name, String description, String... labelNames);

    MaxCounter maxCounter(String name, String description, String... labelNames);

    StaticPercent percent(String name, String description, long total, String... labelNames);

    DynamicPercent dynamicPercent(String name, String description, Gauge gaugeA, Gauge gaugeB);

    Gauge gauge(String name, String description, String... labelNames);

    Histogram histogram(String name, String description, String... labelNames);

    Summary summary(String name, String description, ImmutableSet<Double> quantiles, String... labelNames);

    Info info(String name, String description, String... labelNames);

    default Summary summary(String name, String description, String... labelNames) {
        return summary(name, description, DEFAULT_QUANTILES, labelNames);
    }

    void recurring(Runnable runnable);

    void unregister(Collector metric);

    void unregister(GaugeBasedMetric metric);
}