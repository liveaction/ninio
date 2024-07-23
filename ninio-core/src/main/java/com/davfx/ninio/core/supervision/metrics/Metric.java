package com.davfx.ninio.core.supervision.metrics;

public abstract class Metric {

    private final String name;

    /**
     * Disposable metrics will be discarded after a reset cycle, meaning that if they're not recreated, they won't be displayed anymore.
     */
    private final boolean disposable;

    public Metric(String name) {
        this(name, false);
    }

    public Metric(String name, boolean disposable) {
        this.name = name;
        this.disposable = disposable;
    }

    /**
     * This name will be used in reporting tools to identify the metric
     */
    public final String name(){
        return name;
    }

    /**
     * @return true if the metric is disposable, false otherwise
     */
    public boolean isDisposable() {
        return disposable;
    }

    /**
     * The value that will be displayed by reporting tools
     */
    public abstract String getValue();

    /**
     * resets the Metric to its default state
     */
    public abstract void reset();

    /**
     * Format a String to be considered as a Tag by slf4j
     */
    static String wrapTag(String val) {
        String wrapped = '[' + val + ']';
        return wrapped.toUpperCase();
    }
}
