package com.davfx.ninio.util;

import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;

public final class MemoryCache<K, V> {
	
	private static final Config CONFIG = ConfigUtils.load(new com.davfx.ninio.util.dependencies.Dependencies()).getConfig(MemoryCache.class.getPackage().getName());
	private static final double DEFAULT_CHECK_TIME = ConfigUtils.getDuration(CONFIG, "cache.default.check");

	@VisibleForTesting
	DoubleSupplier nowSupplier = DateUtils::now;

	public static interface Builder<K, V> {
		Builder<K, V> expireAfterAccess(double expiration);
		Builder<K, V> expireAfterWrite(double expiration);
		Builder<K, V> check(double checkTime);
		Builder<K, V> limitedTo(int limit);
		Builder<K, V> keepOrder(boolean keepOrder);
		MemoryCache<K, V> build();
	}
	
	public static <K, V> Builder<K, V> builder() {
		return new Builder<K, V>() {
			private double expirationAfterAccess = 0d;
			private double expirationAfterWrite = 0d;
			private double checkTime = DEFAULT_CHECK_TIME;
			private int limit = 0;
			private boolean keepOrder = false;

			@Override
			public Builder<K, V> expireAfterAccess(double expiration) {
				expirationAfterAccess = expiration;
				return this;
			}
			@Override
			public Builder<K, V> expireAfterWrite(double expiration) {
				expirationAfterWrite = expiration;
				return this;
			}
			@Override
			public Builder<K, V> limitedTo(int limit) {
				this.limit = limit;
				return this;
			}
			@Override
			public Builder<K, V> check(double checkTime) {
				this.checkTime = checkTime;
				return this;
			}

			@Override
			public Builder<K, V> keepOrder(boolean keepOrder) {
				this.keepOrder = keepOrder;
				return this;
			}

			@Override
			public MemoryCache<K, V> build() {
				return new MemoryCache<>(expirationAfterAccess, expirationAfterWrite, limit, checkTime, keepOrder);
			}
		};
	}
	
	private static final class Element<V> {
		public double writeTimestamp;
		public double accessTimestamp;
		public final V v;
		public Element(V v) {
			this.v = v;
		}
	}

	private final double expirationAfterAccess;
	private final double expirationAfterWrite;
	private final int limit;
	private final double checkTime;
	private double lastCheck = 0d;
	private final Map<K, Element<V>> map;
	
	private MemoryCache(double expirationAfterAccess, double expirationAfterWrite, int limit, double checkTime, boolean keepOrder) {
		this.expirationAfterAccess = expirationAfterAccess;
		this.expirationAfterWrite = expirationAfterWrite;
		this.limit = limit;
		this.checkTime = checkTime;
		this.map = keepOrder ? new LinkedHashMap<>() : new HashMap<>();
    }
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder().append('{');
		boolean first = true;
		for (Map.Entry<K, Element<V>> e : map.entrySet()) {
			if (!first) {
				b.append(',');
			} else {
				first = false;
			}
			b.append(e.getKey()).append('=').append(e.getValue().v);
		}
		b.append('}');
		return b.toString();
	}
	
	public void put(K key, V value) {
		try {
			double now = nowSupplier.getAsDouble();
	
			Element<V> e = new Element<>(value);
			e.writeTimestamp = now;
			e.accessTimestamp = now;
			
			Element<V> previousElement = map.put(key, e);

			if (limit > 0 && previousElement==null) { // if previousElement != null, the size has not increased
				if (map.size() > limit) {
					Iterator<Map.Entry<K, Element<V>>> i = map.entrySet().iterator();
					i.next();
					i.remove();
				}
			}
		} finally {
			check();
		}
	}
	
	public V get(K key) {
		try {
			Element<V> e = map.get(key);
			if (e == null) {
				return null;
			}
	
			double now = nowSupplier.getAsDouble();
			
			if (expirationAfterAccess > 0d) {
				if ((now - e.accessTimestamp) >= expirationAfterAccess) {
					map.remove(key);
					return null;
				}
			}
			if (expirationAfterWrite > 0d) {
				if ((now - e.writeTimestamp) >= expirationAfterWrite) {
					map.remove(key);
					return null;
				}
			}
			
			e.accessTimestamp = now;

			return e.v;
		} finally {
			check();
		}
	}

	public void remove(K key) {
		try {
			map.remove(key);
		} finally {
			check();
		}
	}

	public void clear() {
		try {
			map.clear();
		} finally {
			check();
		}
	}
	
	private void check() {
		double now = nowSupplier.getAsDouble();

		if ((now - lastCheck) < checkTime) {
			return;
		}
		
		lastCheck = now;
		
		if (expirationAfterAccess > 0d) {
			map.values().removeIf(vElement -> (now - vElement.accessTimestamp) > expirationAfterAccess);
		}
		if (expirationAfterWrite > 0d) {
			map.values().removeIf(vElement -> (now - vElement.writeTimestamp) > expirationAfterWrite);
		}
	}
	
	public Set<K> keys() {
		check();

		return map.keySet();
	}

	public Stream<V> values() {
		check();
		
		return map.values().stream()
				.map(e -> e.v);
	}
	
	private static final class InnerMapEntry<K, V> implements Map.Entry<K, V> {
		private final Map.Entry<K, Element<V>> e;
		public InnerMapEntry(Map.Entry<K, Element<V>> e) {
			this.e = e;
		}
		public K getKey() {
			return e.getKey();
		}
		public V getValue() {
			return e.getValue().v;
		}
		public int hashCode() {
			return Objects.hash(e.getKey(), e.getValue().v);
		}
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (!(o instanceof InnerMapEntry<?, ?>)) {
				return false;
			}
			@SuppressWarnings("unchecked")
			InnerMapEntry<K, V> a = (InnerMapEntry<K, V>) o;
			return Objects.equals(e.getKey(), a.e.getKey()) && Objects.equals(e.getValue(), a.e.getValue());
		}
		public V setValue(V value) {
			throw new UnsupportedOperationException();
		}
	}

	public List<Map.Entry<K, V>> entries() {
		check();
		
		return map.entrySet().stream()
				.map(InnerMapEntry::new)
				.collect(toImmutableList());
	}
}
