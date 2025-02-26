package com.davfx.ninio.proxy;

import com.davfx.ninio.util.StringUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

public final class ProxyHeader {
	public final String type;
	public final ImmutableMap<String, String> parameters;

	public ProxyHeader(String type, ImmutableMap<String, String> parameters) {
		this.type = type;
		this.parameters = parameters;
	}
	public ProxyHeader(String type) {
		this.type = type;
		parameters = ImmutableMap.of();
	}

	public ProxyHeader withParameter(String name, String value) {
		ImmutableMap<String, String> newParameters = ImmutableMap.<String, String>builder()
				.putAll(parameters)
				.put(name, value)
				.build();
		return new ProxyHeader(type, newParameters);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder(StringUtils.escape(type, ' '));
		for (Map.Entry<String, String> e : parameters.entrySet()) {
			b.append(" ").append(StringUtils.escape(e.getKey(), ' ')).append(" ").append(StringUtils.escape(e.getValue(), ' '));
		}
		return b.toString();
	}
	
	public static ProxyHeader of(String header) {
		List<String> l = Splitter.on(' ').splitToList(header);
		String type = null;
		String key = null;
		ImmutableMap.Builder<String, String> p = ImmutableMap.builder();
		for (String s : l) {
			s = StringUtils.unescape(s, ' ');
			if (type == null) {
				type = s;
			} else if (key == null) {
				key = s;
			} else {
				p.put(key, s);
				key = null;
			}
		}
		return new ProxyHeader(type, p.build());
	}
}
