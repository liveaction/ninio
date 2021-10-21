package com.davfx.ninio.core.supervision.metrics;

import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Timer implementation using the dropwizard implementation
 */
public class TimerMetric extends Metric {
    private Timer timer;

    public TimerMetric(String name) {
        super(name);
        this.timer = new Timer();
    }

    @Override
    public String getValue() {
            Snapshot snapshot = timer.getSnapshot();
        return String.format("count=%d, min=%f, max=%f, mean=%f, stddev=%f, median=%f, " +
                        "p75=%f, p95=%f, p98=%f, p99=%f, p999=%f, mean_rate=%f, rate_unit=events/second, duration_unit=milliseconds",
                timer.getCount(),
                convertDuration(snapshot.getMin()),
                convertDuration(snapshot.getMax()),
                convertDuration(snapshot.getMean()),
                convertDuration(snapshot.getStdDev()),
                convertDuration(snapshot.getMedian()),
                convertDuration(snapshot.get75thPercentile()),
                convertDuration(snapshot.get95thPercentile()),
                convertDuration(snapshot.get98thPercentile()),
                convertDuration(snapshot.get99thPercentile()),
                convertDuration(snapshot.get999thPercentile()),
                timer.getMeanRate()
        );
    }

    @Override
    public void reset() {
        this.timer = new Timer();
    }

    private double convertDuration(double value) {
        return value / 1_000_000;
    }

    // Override all Timer methods to use our Timer

    public void update(long duration, TimeUnit unit) {
        timer.update(duration, unit);
    }

    public void update(Duration duration) {
        timer.update(duration);
    }

    public <T> T time(Callable<T> event) throws Exception {
        return timer.time(event);
    }

    public <T> T timeSupplier(Supplier<T> event) {
        return timer.timeSupplier(event);
    }

    public void time(Runnable event) {
        timer.time(event);
    }

    public Timer.Context time() {
        return timer.time();
    }

    public long getCount() {
        return timer.getCount();
    }

    public double getFifteenMinuteRate() {
        return timer.getFifteenMinuteRate();
    }

    public double getFiveMinuteRate() {
        return timer.getFiveMinuteRate();
    }

    public double getMeanRate() {
        return timer.getMeanRate();
    }

    public double getOneMinuteRate() {
        return timer.getOneMinuteRate();
    }

    public Snapshot getSnapshot() {
        return timer.getSnapshot();
    }
}
