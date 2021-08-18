package com.davfx.ninio.core.supervision.metrics;

public interface LongMetric extends Metric {

    /**
     * This value can be used to perform computations and create composite metrics
     */
    Long value();
}
