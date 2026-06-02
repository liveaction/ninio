package com.davfx.ninio.core.supervision.metrics.pmt;

import com.davfx.ninio.core.supervision.metrics.WithMetrics;
import io.prometheus.metrics.core.metrics.Gauge;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StaticPercentTest {

    @Rule
    public WithMetrics withMetrics = new WithMetrics();

    @Test(expected =  IllegalArgumentException.class)
    public void shouldThrowIllegalArgument_whenDenominatorIsZero() {
        new StaticPercent(withMetrics.metrics().registry(), "queue_free", "Queue free", 0);
    }

    @Test
    public void shouldGetPercent() {
        StaticPercent percent = new StaticPercent(withMetrics.metrics().registry(), "queue_free", "Queue free", 100);
        Gauge gauge = percent.get();

        percent.observe(0);
        assertThat(gauge.get()).isEqualTo(100);
        percent.observe(10);
        assertThat(gauge.get()).isEqualTo(90);
        percent.observe(90);
        assertThat(gauge.get()).isEqualTo(10);
        percent.observe(100);
        assertThat(gauge.get()).isEqualTo(0);
    }

    @Test
    public void shouldGetPercentForLabels() {
        StaticPercent percent = new StaticPercent(withMetrics.metrics().registry(), "queue_free", "Queue free", 100, "thread");
        Gauge gauge = percent.get();

        percent.observe(0, "thread1");
        assertThat(gauge.labelValues("thread1").get()).isEqualTo(100);
        percent.observe(10, "thread1");
        assertThat(gauge.labelValues("thread1").get()).isEqualTo(90);
        percent.observe(90, "thread2");
        assertThat(gauge.labelValues("thread2").get()).isEqualTo(10);
        percent.observe(100, "thread2");
        assertThat(gauge.labelValues("thread2").get()).isEqualTo(0);
    }
}