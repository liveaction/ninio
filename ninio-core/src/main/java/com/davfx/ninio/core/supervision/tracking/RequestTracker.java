package com.davfx.ninio.core.supervision.tracking;

import com.davfx.ninio.core.supervision.metrics.LongMetric;
import com.davfx.ninio.core.supervision.metrics.Metric;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

public final class RequestTracker implements LongMetric {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestTracker.class);

    private final String name;
    private final LongAdder count = new LongAdder();
    private ImmutableSet<String> addressToFollow = ImmutableSet.of();

    public RequestTracker(String name) {
        this.name = name;
    }

    public void track(String address, Function<String, String> logAction) {
        count.increment();
        if (addressToFollow.contains(address)) {
            LOGGER.info("[TRACKING] {} {}", name, logAction.apply(address));
        }
    }

    public void setAddressToFollow(ImmutableSet<String> addressToFollow) {
        this.addressToFollow = addressToFollow;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String getValue() {
        return count.toString();
    }

    @Override
    public void reset() {
        count.reset();
    }

    @Override
    public Long value() {
        return count.sum();
    }
}
