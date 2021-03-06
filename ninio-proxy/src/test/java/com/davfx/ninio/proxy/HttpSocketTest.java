package com.davfx.ninio.proxy;

import com.davfx.ninio.core.*;
import com.davfx.ninio.http.HttpContentReceiver;
import com.davfx.ninio.http.HttpContentSender;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.HttpListeningHandler;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.davfx.ninio.proxy.TestUtil.findAvailablePort;

public class HttpSocketTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(HttpSocketTest.class);

	private int port;

	@Before
	public void setUp() throws Exception {
		port = findAvailablePort();
	}
	
	@Test
	public void test() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			final Wait serverWaitHttpServerClosing = new Wait();
			final Wait serverWaitHttpServerConnecting = new Wait();
			try (Listener httpSocketServer = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)))) {
				httpSocketServer.listen(ninio.create(HttpListening.builder().with(new HttpListeningHandler() {
				@Override
				public HttpContentReceiver handle(HttpRequest request, HttpResponseSender responseHandler) {
					LOGGER.debug("----> {}", request);
					if (request.path.equals("/ws")) {
						final HttpContentSender sender = responseHandler.send(HttpResponse.ok());
						return new HttpContentReceiver() {
							@Override
							public void ended() {
								LOGGER.debug("Socket closed <--");
							}
							@Override
							public void received(ByteBuffer buffer) {
								String s = ByteBufferUtils.toString(buffer);
								LOGGER.debug("Received <--: {}", s);
								sender.send(ByteBufferUtils.toByteBuffer("ECHO " + s), new Nop());
							}
						};
					} else {
						responseHandler.send(HttpResponse.notFound()).finish();
						return null;
					}
				}
				@Override
				public void closed() {
					serverWaitHttpServerClosing.run();
				}
				@Override
				public void connected(Address address) {
					serverWaitHttpServerConnecting.run();
				}
				@Override
				public void failed(IOException ioe) {
					lock.fail(ioe);
				}
			})));
				
				serverWaitHttpServerConnecting.waitFor();
				
				int proxyPort = findAvailablePort();

				Wait serverWaitForProxyServerClosing = new Wait();

				try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), new WaitProxyListening(serverWaitForProxyServerClosing)))) {
					try (ProxyProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)))) {
						Wait clientWaitClosing = new Wait();
						try (Connecter client = ninio.create(proxyClient.http().route("/ws").to(new Address(Address.LOCALHOST, port)))) {
							Wait clientWaitConnecting = new Wait();
							client.connect(
									new WaitConnectedConnection(clientWaitConnecting, 
									new WaitClosedConnection(clientWaitClosing, 
									new LockFailedConnection(lock, 
									new LockReceivedConnection(lock,
									new Nop())))));
								
							Wait clientWaitSent = new Wait();
							client.send(null, ByteBufferUtils.toByteBuffer("test0"),
									new WaitSentSendCallback(clientWaitSent,
									new LockSendCallback(lock,
									new Nop())));
								
							clientWaitConnecting.waitFor();
							Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("ECHO test0");
						}
						clientWaitClosing.waitFor();
					}
				}

				serverWaitForProxyServerClosing.waitFor();
			}
			
			serverWaitHttpServerClosing.waitFor();
		}
	}
	
	@Test
	public void testSameToCheckClose() throws Exception {
		test();
	}
	
	@Test
	public void testDirectlyReceiving() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			final Wait serverWaitHttpServerClosing = new Wait();
			final Wait serverWaitHttpServerConnecting = new Wait();
			try (Listener httpSocketServer = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)))) {
				httpSocketServer.listen(ninio.create(HttpListening.builder().with(new HttpListeningHandler() {
				@Override
				public HttpContentReceiver handle(HttpRequest request, HttpResponseSender responseHandler) {
					LOGGER.debug("----> {}", request);
					if (request.path.equals("/ws")) {
						final HttpContentSender sender = responseHandler.send(HttpResponse.ok());
						sender.send(ByteBufferUtils.toByteBuffer("test1"), new Nop());
						return new HttpContentReceiver() {
							@Override
							public void ended() {
								LOGGER.debug("Socket closed <--");
							}
							@Override
							public void received(ByteBuffer buffer) {
								String s = ByteBufferUtils.toString(buffer);
								LOGGER.debug("Received <--: {}", s);
							}
						};
					} else {
						responseHandler.send(HttpResponse.notFound()).finish();
						return null;
					}
				}
				@Override
				public void closed() {
					serverWaitHttpServerClosing.run();
				}
				@Override
				public void connected(Address address) {
					serverWaitHttpServerConnecting.run();
				}
				@Override
				public void failed(IOException ioe) {
					lock.fail(ioe);
				}
			})));
				
				serverWaitHttpServerConnecting.waitFor();
				
				int proxyPort = findAvailablePort();

				Wait serverWaitForProxyServerClosing = new Wait();

				Wait clientWaitConnecting = new Wait();
				Wait clientWaitClosing = new Wait();

				try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), new WaitProxyListening(serverWaitForProxyServerClosing)))) {
					try (ProxyProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)))) {
						try (Connecter client = ninio.create(proxyClient.http().route("/ws").to(new Address(Address.LOCALHOST, port)))) {
							client.connect(
									new WaitConnectedConnection(clientWaitConnecting, 
									new WaitClosedConnection(clientWaitClosing, 
									new LockFailedConnection(lock, 
									new LockReceivedConnection(lock,
									new Nop())))));
							
							clientWaitConnecting.waitFor();
							Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("test1");
						}
						clientWaitClosing.waitFor();
					}
				}

				serverWaitForProxyServerClosing.waitFor();
			}
			
			serverWaitHttpServerClosing.waitFor();
		}
	}
	
	@Test
	public void testDirectlyReceivingSameToCheckClose() throws Exception {
		testDirectlyReceiving();
	}
}
