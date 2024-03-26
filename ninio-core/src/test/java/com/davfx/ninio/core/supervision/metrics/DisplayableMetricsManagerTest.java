package com.davfx.ninio.core.supervision.metrics;

import com.google.common.collect.Maps;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DisplayableMetricsManagerTest {

    private DisplayableMetricsManager tested;

    @Before
    public void setUp() throws Exception {
        tested = DisplayableMetricsManager.instance();
    }

    @Test
    public void shouldRemoveDisposableMetricOnClear() {
        CounterMetric metric = new CounterMetric("metric");
        CounterMetric disposableMetric = new CounterMetric("disposableMetric");

        tested.addIfAbsent(metric, new MetricsParams(false, metric.name()));
        tested.addIfAbsent(disposableMetric, new MetricsParams(true, disposableMetric.name()));

        assertThat(tested.getMetrics()).contains(Maps.immutableEntry("metric", metric), Maps.immutableEntry("disposableMetric", disposableMetric));

        tested.clear();

        assertThat(tested.getMetrics()).contains(Maps.immutableEntry("metric", metric));
        assertThat(tested.getMetrics()).doesNotContain(Maps.immutableEntry("disposableMetric", metric));
    }
}