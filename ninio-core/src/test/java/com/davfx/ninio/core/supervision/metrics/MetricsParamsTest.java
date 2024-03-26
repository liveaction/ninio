package com.davfx.ninio.core.supervision.metrics;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MetricsParamsTest {

    @Test
    public void shouldNotBeDisposableByDefault() {
        MetricsParams metricsParams = new MetricsParams("all", "our", "gods", "have", "abandoned", "us");

        assertThat(metricsParams.tags()).containsExactly("all", "our", "gods", "have", "abandoned", "us");
        assertThat(metricsParams.isDisposable()).isFalse();
    }
}