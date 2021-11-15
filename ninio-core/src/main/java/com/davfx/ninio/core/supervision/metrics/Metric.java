package com.davfx.ninio.core.supervision.metrics;

public abstract class Metric {

    private final String name;

    public Metric(String name) {
        this.name = name;
    }

    /**
     * This name will be used in reporting tools to identify the metric
     */
    public final String name(){
        return name;
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
