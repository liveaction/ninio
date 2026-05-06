package com.davfx.ninio.core.supervision.metrics.pmt;

import com.davfx.ninio.core.supervision.metrics.WithMetrics;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MaxCounterTest {

    @Rule
    public WithMetrics withMetrics = new WithMetrics();

    @Test
    public void shouldKeepMax() {
        MaxCounter maxCounter = new MaxCounter(withMetrics.metrics().registry(), "delay", "Maximum delay");

        maxCounter.observe(2);
        maxCounter.observe(3);
        maxCounter.observe(1);

        assertThat(maxCounter.get().get()).isEqualTo(3);
    }

    @Test
    public void shouldKeepMax_forLabels() {
        MaxCounter maxCounter = new MaxCounter(withMetrics.metrics().registry(), "delay", "Maximum delay by protocol", "protocol");

        maxCounter.observe(2, "udp");
        maxCounter.observe(1, "udp");
        maxCounter.observe(5, "tcp");
        maxCounter.observe(3, "tcp");
        maxCounter.observe(1, "tcp");

        assertThat(maxCounter.get().labelValues("udp").get()).isEqualTo(2);
        assertThat(maxCounter.get().labelValues("tcp").get()).isEqualTo(5);
    }

    @Test
    public void shouldResetToZero() {
        MaxCounter maxCounter = new MaxCounter(withMetrics.metrics().registry(), "delay", "Maximum delay");

        maxCounter.observe(3);

        assertThat(maxCounter.get().get()).isEqualTo(3);

        maxCounter.reset();

        assertThat(maxCounter.get().get()).isEqualTo(0);
    }
}