package com.davfx.ninio.proxy;

import com.davfx.ninio.core.*;
import com.davfx.ninio.dns.DnsClient;
import com.davfx.ninio.dns.DnsConnecter;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.http.HttpConnecter;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.WebsocketHttpListeningHandler;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.davfx.ninio.proxy.TestUtil.findAvailablePort;

public class WebsocketSocketTest {

	@Test
	public void test() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		final Wait serverWaitServerConnecting = new Wait();
		final Wait serverWaitServerClosing = new Wait();
		final Wait serverWaitClientConnecting = new Wait();
		final Wait serverWaitClientClosing = new Wait();

		int port = findAvailablePort();
		try (Ninio ninio = Ninio.create()) {
			try (Listener tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)))) {
				tcp.listen(ninio.create(HttpListening.builder().with(new WebsocketHttpListeningHandler(true, new Listening() {
					@Override
					public void closed() {
						serverWaitServerClosing.run();
					}
					@Override
					public void failed(IOException e) {
						lock.fail(e);
					}
					@Override
					public void connected(Address address) {
						serverWaitServerConnecting.run();
					}
					
					@Override
					public Connection connecting(final Connected connecting) {
						return new Connection() {
							private final InMemoryBuffers buffers = new InMemoryBuffers();
							@Override
							public void received(Address address, ByteBuffer buffer) {
								buffers.add(buffer);
								String s = buffers.toString();
								if (s.indexOf('\n') >= 0) {
									connecting.send(null, ByteBufferUtils.toByteBuffer("ECHO " + s), new Nop());
								}
							}
							
							@Override
							public void failed(IOException ioe) {
								lock.fail(ioe);
							}
							@Override
							public void connected(Address address) {
								serverWaitClientConnecting.run();
							}
							@Override
							public void closed() {
								serverWaitClientClosing.run();
							}
						};
					}
				}))));
				
				serverWaitServerConnecting.waitFor();
	
				Wait serverWaitForProxyServerClosing = new Wait();

				int proxyPort = findAvailablePort();

				try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultUnsecureServer(new Address(Address.ANY, proxyPort),
						new WaitProxyListening(serverWaitForProxyServerClosing)))) {
					try (ProxyProvider proxyClient = ninio.create(ProxyClient.defaultUnsecureClient(new Address(Address.LOCALHOST, proxyPort)))) {
						try (DnsConnecter dns = ninio.create(DnsClient.builder()); HttpConnecter httpClient = ninio.create(HttpClient.builder().with(dns))) {
							Wait clientWaitClosing = new Wait();
							try (Connecter client = ninio.create(proxyClient.websocket().route("/ws").to(new Address(Address.LOCALHOST, port)))) {
								Wait clientWaitConnecting = new Wait();
								client.connect(
										new WaitConnectedConnection(clientWaitConnecting, 
										new WaitClosedConnection(clientWaitClosing, 
										new LockFailedConnection(lock, 
										new LockReceivedConnection(lock,
										new Nop())))));
								
								clientWaitConnecting.waitFor();
								final Wait clientWaitSending0 = new Wait();
								client.send(null, ByteBufferUtils.toByteBuffer("test0"), new SendCallback() {
									@Override
									public void failed(IOException e) {
										lock.fail(e);
									}
									@Override
									public void sent() {
										clientWaitSending0.run();
									}
								});
								final Wait clientWaitSending1 = new Wait();
								client.send(null, ByteBufferUtils.toByteBuffer("test1\n"), new SendCallback() {
									@Override
									public void failed(IOException e) {
										lock.fail(e);
									}
									@Override
									public void sent() {
										clientWaitSending1.run();
									}
								});
								clientWaitSending0.waitFor();
								clientWaitSending1.waitFor();
								Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("ECHO test0test1\n");
							}
							clientWaitClosing.waitFor();
						}
					}
				}

				serverWaitForProxyServerClosing.waitFor();
			}
			serverWaitServerClosing.waitFor();
		}
	}
	
	@Test
	public void testSameToCheckClose() throws Exception {
		test();
	}
	
}
