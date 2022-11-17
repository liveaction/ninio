package com.davfx.ninio.util;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class MemoryCacheTest {
	@Test
	public void testExpireAfterWrite() throws Exception {
		MemoryCache<String, String> cache = MemoryCache.<String, String> builder().expireAfterWrite(0.1d).build();
		cache.nowSupplier = () -> 0;
		cache.put("k", "v");
		Assertions.assertThat(cache.get("k")).isEqualTo("v");
		cache.nowSupplier = () -> 0.05d;
		Assertions.assertThat(cache.get("k")).isEqualTo("v");
		cache.nowSupplier = () -> 0.12d;
		Assertions.assertThat(cache.get("k")).isNull();
	}

	@Test
	public void testExpireAfterAccess() throws Exception {
		MemoryCache<String, String> cache = MemoryCache.<String, String> builder().expireAfterAccess(0.1d).build();
		cache.nowSupplier = () -> 0;
		cache.put("k", "v");
		Assertions.assertThat(cache.get("k")).isEqualTo("v");
		cache.nowSupplier = () -> 0.05d;
		Assertions.assertThat(cache.get("k")).isEqualTo("v");
		cache.nowSupplier = () -> 0.12d;
		Assertions.assertThat(cache.get("k")).isEqualTo("v");
		cache.nowSupplier = () -> 0.27d;
		Assertions.assertThat(cache.get("k")).isNull();
	}
}
