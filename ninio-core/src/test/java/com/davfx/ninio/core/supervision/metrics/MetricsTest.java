package com.davfx.ninio.core.supervision.metrics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MetricsTest {

    private final String expectedName = "[TEST]";
    private final String actualName = "test";

    @Test
    public void counterTest() {
        CounterMetric c = new CounterMetric(actualName);

        assertEquals(c.name(), expectedName);

        c.inc();
        assertEquals((long) c.value(), 1L);

        c.add(9L);
        assertEquals((long) c.value(), 10L);

        c.dec();
        assertEquals((long) c.value(), 9L);

        c.add(-8L);
        assertEquals((long) c.value(), 1L);

        c.reset();
        assertEquals((long) c.value(), 0L);
    }

    @Test
    public void maxCounterTest() {
        MaxCounterMetric c = new MaxCounterMetric(actualName);

        assertEquals(c.name(), expectedName);

        c.inc();
        assertEquals((long) c.value(), 1L);

        c.add(9L);
        assertEquals((long) c.value(), 10L);

        c.dec();
        assertEquals((long) c.value(), 10L);

        c.add(-8L);
        assertEquals((long) c.value(), 10L);

        c.reset();
        assertNull(c.value());

        c.inc();
        assertEquals((long) c.value(), 1L);



    }

    @Test
    public void percentTest() {
        CounterMetric c1 = new CounterMetric("c1");
        CounterMetric c2 = new CounterMetric("c2");
        PercentMetric percent = new PercentMetric(c1, c2);

        c1.add(100L);

        c2.add(10L);
        assertEquals((long) percent.value(), 90L);

        c2.add(40L);
        assertEquals((long) percent.value(), 50L);

        c1.add(100L);
        assertEquals((long) percent.value(), 75L);

        percent.reset();
        assertEquals((long) c1.value(), 0L);
        assertEquals((long) c2.value(), 0L);
        assertNull(percent.value());
    }

}