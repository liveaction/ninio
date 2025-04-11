package com.davfx.ninio.core.supervision.metrics;

import java.util.function.Supplier;

public final class SupplierMetric extends Metric {

    private final Supplier<String> supplier;

    public SupplierMetric(String name, Supplier<String> supplier) {
        super(name);
        this.supplier = supplier;
    }

    @Override
    public String getValue() {
        return this.supplier.get();
    }

    @Override
    public void reset() {
        //
    }
}
