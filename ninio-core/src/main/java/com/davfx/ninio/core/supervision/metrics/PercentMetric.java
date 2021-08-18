package com.davfx.ninio.core.supervision.metrics;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Percentage between 2 Metrics: (metricA - metricB) / metricA
 */
public final class PercentMetric implements LongMetric {
    public final LongMetric metricA;
    public final LongMetric metricB;
    public final String name;

    public PercentMetric(LongMetric metricA, LongMetric metricB, String... tags) {
        this.metricA = metricA;
        this.metricB = metricB;
        name = Arrays.stream(tags)
                .map(Metric::wrapTag)
                .collect(Collectors.joining(" "));
    }

    @Override
    public String getValue() {
        long av = metricA.value();
        long bv = metricB.value();
        if (av == 0 && bv == 0) {
            return null;
        }
        return format(percent(av, bv));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void reset() {
        metricA.reset();
        metricB.reset();
    }

    private String format(Double value) {
        return value == null ? "" : String.format("%.2f", value) + "%";
    }

    /**
     * if out = 0, returns null. Otherwise, returns the percentage
     */
    private Double percent(long out, long in) {
        if (out == 0) {
            return null;
        }
        return ((double) (out - in)) * 100d / ((double) out);
    }

    @Override
    public Long value() {
        long av = metricA.value();
        long bv = metricB.value();
        if (av == 0 && bv == 0) {
            return null;
        }
        Double percent = percent(av, bv);
        return percent == null ? null : percent.longValue();
    }
}