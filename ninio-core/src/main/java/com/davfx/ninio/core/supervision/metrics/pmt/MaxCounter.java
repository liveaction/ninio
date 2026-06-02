package com.davfx.ninio.core.supervision.metrics.pmt;

import io.prometheus.metrics.core.datapoints.GaugeDataPoint;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

public final class MaxCounter implements GaugeBasedMetric {

    private final Gauge gauge;

    public MaxCounter(PrometheusRegistry registry, String name, String description, String... labelNames) {
        this.gauge = Gauge.builder()
                .name(name)
                .help(description)
                .labelNames(labelNames)
                .register(registry);
    }

    public void observe(double value) {
        observe(gauge, value);
    }

    public void observe(double value, String... labelValues) {
        observe(gauge.labelValues(labelValues), value);
    }

    private void observe(GaugeDataPoint gauge, double value) {
        double currentValue = gauge.get();

        if (currentValue < value) {
            gauge.set(value);
        }
    }

    public void reset() {
        gauge.set(0);
    }

    @Override
    public Gauge get() {
        return gauge;
    }
}
