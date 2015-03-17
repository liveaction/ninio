package com.davfx.ninio.remote;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.davfx.ninio.common.ClassThreadFactory;
import com.davfx.ninio.common.Closeable;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class WaitingRemoteClientConfigurator implements Closeable {
	private static final Config CONFIG = ConfigUtils.load(WaitingRemoteClientConfigurator.class);
	
	private static final double DEFAULT_TIMEOUT = ConfigUtils.getDuration(CONFIG, "remote.waiting.timeout");
	private static final double DEFAULT_CALL_WITH_EMPTY_TIME = ConfigUtils.getDuration(CONFIG, "remote.waiting.callWithEmptyTime");
	
	public final ScheduledExecutorService callWithEmptyExecutor;
	private final boolean callWithEmptyExecutorToShutdown;
	
	//%% public double endOfCommandTime = ConfigUtils.getDuration(CONFIG, "remote.waiting.endOfCommandTime");
	public double timeout = DEFAULT_TIMEOUT;
	
	public double callWithEmptyTime = DEFAULT_CALL_WITH_EMPTY_TIME;

	private WaitingRemoteClientConfigurator(ScheduledExecutorService callWithEmptyExecutor, boolean callWithEmptyExecutorToShutdown) {
		this.callWithEmptyExecutor = callWithEmptyExecutor;
		this.callWithEmptyExecutorToShutdown = callWithEmptyExecutorToShutdown;
	}
	
	public WaitingRemoteClientConfigurator() throws IOException {
		this(Executors.newSingleThreadScheduledExecutor(new ClassThreadFactory(WaitingRemoteClientConfigurator.class)), true);
	}

	public WaitingRemoteClientConfigurator(ScheduledExecutorService callWithEmptyExecutor) {
		this(callWithEmptyExecutor, false);
	}

	@Override
	public void close() {
		if (callWithEmptyExecutorToShutdown) {
			callWithEmptyExecutor.shutdown();
		}
	}
	
	public WaitingRemoteClientConfigurator(WaitingRemoteClientConfigurator configurator) {
		callWithEmptyExecutorToShutdown = false;
		callWithEmptyExecutor = configurator.callWithEmptyExecutor;
		//%% endOfCommandTime = configurator.endOfCommandTime;
		timeout = configurator.timeout;
		callWithEmptyTime = configurator.callWithEmptyTime;
	}
	
	/*%%
	public WaitingRemoteClientConfigurator withEndOfCommandTime(double endOfCommandTime) {
		this.endOfCommandTime = endOfCommandTime;
		return this;
	}
	*/
	public WaitingRemoteClientConfigurator withTimeout(double timeout) {
		this.timeout = timeout;
		return this;
	}

	public WaitingRemoteClientConfigurator withCallWithEmptyTime(double callWithEmptyTime) {
		this.callWithEmptyTime = callWithEmptyTime;
		return this;
	}

}
