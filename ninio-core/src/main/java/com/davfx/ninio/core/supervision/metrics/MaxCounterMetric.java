package com.davfx.ninio.core.supervision.metrics;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class MaxCounterMetric implements LongMetric {
    private final String name;
    private final LongAdder current = new LongAdder();
    private long max = Long.MIN_VALUE;

    public MaxCounterMetric(String... tags) {
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
        return max == Long.MIN_VALUE ? null : Long.toString(max);
    }

    @Override
    public void reset() {
        current.reset();
        max = Long.MIN_VALUE;
    }

    @Override
    public Long value() {
        return max == Long.MIN_VALUE ? null : max;
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
