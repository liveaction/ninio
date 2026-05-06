package com.davfx.ninio.core.supervision.tracking;

import com.davfx.ninio.util.LogTag;
import com.google.common.collect.ImmutableSet;
import io.prometheus.metrics.core.metrics.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.function.Function;

public final class RequestTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestTracker.class);

    private final Counter counter;
    private ImmutableSet<String> addressToFollow = ImmutableSet.of();

    public RequestTracker(Counter counter) {
        this.counter = counter;
    }

    public void track(String address, Function<String, String> logAction, String... labelValues) {
        boolean hasLabelValues = !Arrays.asList(labelValues).isEmpty();

        if (hasLabelValues) {
            counter.labelValues(labelValues).inc();
        } else {
            counter.inc();
        }

        if (addressToFollow.contains(address)) {
            if (hasLabelValues) {
                LOGGER.info("{} {} ({}) {}", LogTag.TRACKING, counter.getPrometheusName(), labelValues, logAction.apply(address));
            } else {
                LOGGER.info("{} {} {}", LogTag.TRACKING, counter.getPrometheusName(), logAction.apply(address));
            }
        }
    }

    public void setAddressToFollow(ImmutableSet<String> addressToFollow) {
        this.addressToFollow = addressToFollow;
    }

    public Counter counter() {
        return counter;
    }
}
