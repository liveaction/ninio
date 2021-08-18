package com.davfx.ninio.core.supervision.metrics;

public interface Metric {

    /**
     * This name will be used in reporting tools to identify the metric
     */
    String name();

    /**
     * The value that will be displayed by reporting tools
     */
    String getValue();

    /**
     * resets the Metric to its default state
     */
    void reset();

    /**
     * Format a String to be considered as a Tag by slf4j
     */
    static String wrapTag(String val) {
        String wrapped = '[' + val + ']';
        return wrapped.toUpperCase();
    }
}
