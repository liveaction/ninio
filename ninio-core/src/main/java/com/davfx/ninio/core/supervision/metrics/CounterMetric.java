package com.davfx.ninio.core.supervision.metrics;

import java.util.concurrent.atomic.LongAdder;

public class CounterMetric extends LongMetric {
    private final LongAdder value = new LongAdder();

    public CounterMetric(String name) {
        super(name);
    }

    public String getValue() {
        return value.toString();
    }

    public void reset() {
        value.reset();
    }

    public Long value() {
        return value.sum();
    }

    public void add(long v) {
        value.add(v);
    }

    public void inc() {
        value.increment();
    }

    public void dec() {
        value.decrement();
    }
}
