package com.davfx.ninio.core.supervision.metrics.pmt;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

public final class DynamicPercent extends Percent {

    private final Gauge gaugeA;

    public DynamicPercent(PrometheusRegistry registry, String name, String description, Gauge gaugeA, Gauge gaugeB) {
        super(registry, name, description, gaugeB::get);
        this.gaugeA = gaugeA;
    }

    public void observe() {
        observe(gaugeA.get());
    }
}
