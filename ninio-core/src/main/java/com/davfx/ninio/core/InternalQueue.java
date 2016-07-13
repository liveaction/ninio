package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.ClassThreadFactory;

final class InternalQueue implements Queue, AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(InternalQueue.class);

	private static final double WAIT_ON_SELECTOR_ERROR = 10d;
	
	private final Selector selector;
	private final ConcurrentLinkedQueue<Runnable> toRun = new ConcurrentLinkedQueue<Runnable>(); // Using LinkedBlockingQueue my prevent OutOfMemory errors but may DEADLOCK

	public InternalQueue() {
		try {
			selector = SelectorProvider.provider().openSelector();
		} catch (IOException ioe) {
			LOGGER.error("Could not create selector", ioe);
			throw new RuntimeException(ioe);
		}

		Thread t = new ClassThreadFactory(InternalQueue.class).newThread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						try {
							selector.select();
						} catch (ClosedSelectorException ce) {
							return;
						}
						Set<SelectionKey> s;
						try {
							s = selector.selectedKeys();
						} catch (ClosedSelectorException ce) {
							return;
						}
						if (s != null) {
							for (SelectionKey key : s) {
								try {
									((SelectionKeyVisitor) key.attachment()).visit(key);
								} catch (Throwable e) {
									LOGGER.error("Error in event handling", e);
								}
							}
							s.clear();
						}
					} catch (Throwable e) {
						LOGGER.error("Error in selector ", e);
						try {
							Thread.sleep((long) (WAIT_ON_SELECTOR_ERROR * 1000d));
						} catch (InterruptedException ie) {
						}
					}

					while (!toRun.isEmpty()) {
						Runnable r = toRun.poll();
						try {
							r.run();
						} catch (Throwable e) {
							LOGGER.error("Error in running task", e);
						}
					}
				}
			}
		});
		
		t.setDaemon(true);
		t.start();
	}
	
	@Override
	public void execute(Runnable command) {
		toRun.add(command);
		selector.wakeup();
	}
	
	@Override
	public SelectionKey register(SelectableChannel channel) throws ClosedChannelException {
		return channel.register(selector, 0);
	}
	
	/*%%
	@Override
	public void waitFor() {
		Wait w = new Wait();
		execute(w);
		w.waitFor();
	}
	*/
	
	@Override
	public void close() {
		try {
			selector.close();
		} catch (IOException e) {
		}
		selector.wakeup();
	}
}
