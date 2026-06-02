package com.davfx.ninio.core.supervision.metrics.pmt;

import com.davfx.ninio.core.supervision.metrics.WithMetrics;
import io.prometheus.metrics.core.metrics.Gauge;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicPercentTest {

    @Rule
    public WithMetrics withMetrics = new WithMetrics();

    @Test
    public void shouldGetPercent() {
        Gauge in = withMetrics.metrics().gauge("in", "in");
        Gauge out = withMetrics.metrics().gauge("out", "out");
        DynamicPercent percent = new DynamicPercent(withMetrics.metrics().registry(), "lost", "Lost", in, out);
        Gauge gauge = percent.get();

        in.set(1);
        out.set(10);

        percent.observe();
        assertThat(gauge.get()).isEqualTo(90);
    }
}