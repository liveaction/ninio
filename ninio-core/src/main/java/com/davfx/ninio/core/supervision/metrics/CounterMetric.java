package com.davfx.ninio.core.supervision.metrics;

import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class CounterMetric implements LongMetric {
    private final String name;
    private final LongAdder value = new LongAdder();

    public CounterMetric(String... tags) {
        name = Arrays.stream(tags)
                .map(Metric::wrapTag)
                .collect(Collectors.joining(" "));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String getValue() {
        return value.toString();
    }

    @Override
    public void reset() {
        value.reset();
    }

    @Override
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
