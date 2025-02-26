package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.ping.PingClient;
import com.davfx.ninio.ping.PingConnecter;
import com.davfx.ninio.ping.PingConnection;
import com.davfx.ninio.ping.PingReceiver;
import com.davfx.ninio.ping.PingTimeout;
import com.davfx.ninio.util.Lock;

import java.io.IOException;

import static com.davfx.ninio.proxy.TestUtil.DEFAULT_RECIPIENT_ID;

//mvn install dependency:copy-dependencies
//sudo java -cp target/dependency/*:target/test-classes/:target/classes/ com.davfx.ninio.proxy.PingTest
public class PingTest {

	public static void main(String[] args) throws Exception {
		byte[] pingHost = new byte[] { 8, 8, 8, 8 };
		// ::1

		int proxyPort = 8081;
		
		try (Ninio ninio = Ninio.create()) {
			final Lock<Double, IOException> lock = new Lock<>();
			
			try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultUnsecureServer(new Address(Address.ANY, proxyPort), null))) {
				try (ProxyProvider proxyClient = ninio.create(ProxyClient.defaultUnsecureClient(new Address(Address.LOCALHOST, proxyPort)))) {
					try (PingConnecter client = PingTimeout.wrap(1d, ninio.create(PingClient.builder().with(proxyClient.raw(DEFAULT_RECIPIENT_ID))))) {
						client.connect(new PingConnection() {
							@Override
							public void failed(IOException ioe) {
								lock.fail(ioe);
							}
							@Override
							public void connected(Address address) {
							}
							@Override
							public void closed() {
							}
						});
						client.ping(pingHost, new PingReceiver() {
							@Override
							public void received(double time) {
								lock.set(time);
							}
							@Override
							public void failed(IOException ioe) {
								lock.fail(ioe);
							}
						});
						
						System.out.println(lock.waitFor());
					}
				}
			}
		}
	}
	
}
