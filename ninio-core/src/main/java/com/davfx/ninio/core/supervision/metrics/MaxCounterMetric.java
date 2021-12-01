package com.davfx.ninio.core.supervision.metrics;

import java.util.concurrent.atomic.LongAdder;

public class MaxCounterMetric extends LongMetric {
    private final LongAdder current = new LongAdder();
    private long max = Long.MIN_VALUE;

    public MaxCounterMetric(String name) {
        super(name);
    }

    @Override
    public String getValue() {
        return max == Long.MIN_VALUE ? "0" : Long.toString(max);
    }

    @Override
    public void reset() {
        current.reset();
        max = Long.MIN_VALUE;
    }

    @Override
    public Long value() {
        return max == Long.MIN_VALUE ? 0L : max;
    }

    public void add(long v) {
        current.add(v);
        max = Long.max(max, current.sum());
    }

    public void inc() {
        current.increment();
        max = Long.max(max, current.sum());
    }

    public void dec() {
        current.decrement();
    }
}
