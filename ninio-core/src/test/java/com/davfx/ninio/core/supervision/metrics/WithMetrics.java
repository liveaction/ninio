package com.davfx.ninio.core.supervision.metrics;

import com.davfx.ninio.core.supervision.metrics.pmt.PmtMetrics;
import com.davfx.ninio.core.supervision.metrics.pmt.PmtMetricsImpl;
import org.junit.rules.ExternalResource;

public final class WithMetrics extends ExternalResource {

    private PmtMetrics metrics;

    public WithMetrics() {
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        metrics = PmtMetricsImpl.get();
    }

    @Override
    protected void after() {
        super.after();
        metrics.registry().clear();
    }

    public PmtMetrics metrics() {
        return metrics;
    }
}
