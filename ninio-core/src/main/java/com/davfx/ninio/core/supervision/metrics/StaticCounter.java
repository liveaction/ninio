package com.davfx.ninio.core.supervision.metrics;

public final class StaticCounter extends LongMetric {

    private final long value;

    public StaticCounter(String name, long value) {
        super(name);
        this.value = value;
    }

    /**
     * Can be used when no name is needed, only the value, for example in a {@link PercentMetric}
     * @param value the static counter value
     */
    public StaticCounter(long value) {
        this("", value);
    }

    @Override
    public Long value() {
        return value;
    }

    @Override
    public void reset() {

    }
}
