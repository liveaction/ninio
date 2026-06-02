package com.davfx.ninio.core.supervision.metrics.pmt;

import io.prometheus.metrics.model.registry.PrometheusRegistry;

public final class StaticPercent extends Percent {

    public StaticPercent(PrometheusRegistry registry, String name, String description, long total, String... labelNames) {
        super(registry, name, description, () -> (double) total, labelNames);

        if (total == 0) {
            throw new IllegalArgumentException("Static Percent %s cannot be zero".formatted(name));
        }
    }
}
