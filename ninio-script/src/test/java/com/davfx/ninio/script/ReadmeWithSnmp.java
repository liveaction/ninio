package com.davfx.ninio.script;

public final class ReadmeWithSnmp {
	public static void main(String[] args) throws Exception {
		try (ExtendedScriptRunner runner = new ExtendedScriptRunner()) {
			runner.runner.engine().eval("snmp("
					+ "{"
						+ "'host': '127.0.0.1',"
						+ "'oid': '1.3.6.1.2.1.1.4.0',"
						+ "'community': 'public'"
					+ "}, function(r) {"
							+ "console.debug(JSON.stringify(r));"
						+ "}"
				+ ");", null);
			
			Thread.sleep(10000);
		}
	}
}
