package com.davfx.ninio.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckAllocationObject {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CheckAllocationObject.class);
	
	private static final class CountMax {
		private final List<Time> times = new ArrayList<>();
		public int count = 0;
		public int max = 0;
		public String inc(Time time) {
			int c;
			int m;
			synchronized (this) {
				times.add(time);
				count++;
				if (count > max) {
					max = count;
				}
				c = count;
				m = max;
			}
			return c + " (max " + m + ")";
		}
		public synchronized String dec(Time time) {
			int c;
			int m;
			long t;
			synchronized (this) {
				times.remove(time);
				count--;
				c = count;
				m = max;
				if (times.isEmpty()) {
					t = -1L;
				} else {
					t = times.get(0).timestamp;
				}
			}

			if (t < 0L) {
				return c + " (max " + m + ")";
			}

			long delta = (System.currentTimeMillis() - t) / 1000L;
			long min = delta / 60L;
			long sec = delta - (min * 60L);
			return c + " (max " + m + ", oldest " + min + " min " + sec + " sec ago)";
		}
	}
	
	private static final class Time {
		public final long timestamp = System.currentTimeMillis();
	}
	
	private static final Map<Class<?>, CountMax> COUNTS = new HashMap<>();

	private final String prefix;
	private final CountMax count;
	private final Time time = new Time();
	
	public CheckAllocationObject(Class<?> clazz) {
		prefix = clazz.getName();
		CountMax c;
		synchronized (COUNTS) {
			c = COUNTS.get(clazz);
			if (c == null) {
				c = new CountMax();
				COUNTS.put(clazz, c);
			}
		}
		count = c;
		
		String x = count.inc(time);
		LOGGER.debug("*** {} | Allocation inc: {}", prefix, x);
	}
	
	@Override
	protected void finalize() {
		String x = count.dec(time);
		LOGGER.debug("*** {} | Allocation dec: {}", prefix, x);
	}
}
