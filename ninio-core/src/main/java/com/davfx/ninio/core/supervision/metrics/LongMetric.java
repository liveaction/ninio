package com.davfx.ninio.core.supervision.metrics;

public abstract class LongMetric extends Metric {

    public LongMetric(String name) {
        super(name);
    }

    /**
     * This value can be used to perform computations and create composite metrics
     */
    public abstract Long value();
}
