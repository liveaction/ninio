package com.davfx.ninio.core.supervision.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class MaxCounterMetric extends LongMetric {
    private final AtomicLong current = new AtomicLong();
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
        max = current.get();
    }

    @Override
    public Long value() {
        return max == Long.MIN_VALUE ? 0L : max;
    }

    public void add(long v) {
        long l = current.addAndGet(v);
        max = Long.max(max, l);
    }

    public void inc() {
        long l = current.incrementAndGet();
        max = Long.max(max, l);
    }

    public void dec() {
        current.decrementAndGet();
    }
}
