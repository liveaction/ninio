package com.davfx.ninio.core.supervision.metrics;

import com.google.common.collect.ImmutableList;

import java.util.Objects;

public final class MetricsParams {

    private final ImmutableList<String> tags;
    private final boolean disposable;

    public MetricsParams(String... tags) {
        this(false, tags);
    }

    public MetricsParams(boolean disposable, String... tags) {
        this.tags = ImmutableList.copyOf(tags);
        this.disposable = disposable;
    }

    public ImmutableList<String> tags() {
        return tags;
    }

    public boolean isDisposable() {
        return disposable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricsParams that = (MetricsParams) o;
        return disposable == that.disposable && Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tags, disposable);
    }

    @Override
    public String toString() {
        return "MetricsParams{" +
                "tags=" + tags +
                ", disposable=" + disposable +
                '}';
    }
}
