package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.NinioProvider;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.TcpSocket;

public final class TelnetClient {
	/*%%
	public static void main(String[] args) throws Exception {
		try (Ninio ninio = Ninio.create()) {
			Connector c = ninio.create(TelnetClient.builder().receiving(new Receiver() {
				private int n = 0;
				@Override
				public void received(Address address, ByteBuffer buffer) {
					System.out.println(n + " ---> "+ new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining(), Charsets.UTF_8));
					switch (n) {
					case 1:
						connector.send(null, ByteBuffer.wrap(("davidfauthoux" + TelnetSpecification.EOL).getBytes(Charsets.UTF_8)));
						break;
					case 3:
						connector.send(null, ByteBuffer.wrap(("mypassword" + TelnetSpecification.EOL).getBytes(Charsets.UTF_8)));
						break;
					case 5:
						connector.send(null, ByteBuffer.wrap(("ls" + TelnetSpecification.EOL).getBytes(Charsets.UTF_8)));
						break;
					}
					n++;
				}
			}).with(TcpSocket.builder().to(new Address(Address.LOCALHOST, TelnetSpecification.DEFAULT_PORT))));
			try {
				Thread.sleep(100000);
			} finally {
				c.close();
			}
		}
	}
	*/
	
	public static interface Builder extends NinioBuilder<Connecter> {
		Builder with(TcpSocket.Builder builder);
	}

	public static Builder builder() {
		return new Builder() {
			private TcpSocket.Builder builder = null;
			
			@Override
			public Builder with(TcpSocket.Builder builder) {
				this.builder = builder;
				return this;
			}
			
			@Override
			public Connecter create(NinioProvider ninioProvider) {
				if (builder == null) {
					throw new NullPointerException("builder");
				}
				
				final Connecter connecter = builder.create(ninioProvider);

				return new Connecter() {
					@Override
					public void connect(final Connection callback) {
						final TelnetReader telnetReader = new TelnetReader();

						connecter.connect(new Connection() {
							@Override
							public void closed() {
								callback.closed();
							}
							@Override
							public void connected(Address address) {
								callback.connected(address);
							}
							@Override
							public void failed(IOException ioe) {
								callback.failed(ioe);
							}
							
							@Override
							public void received(Address address, ByteBuffer buffer) {
								telnetReader.handle(buffer, callback, connecter);
							}
						});
					}
					
					@Override
					public void send(Address address, ByteBuffer buffer, SendCallback callback) {
						connecter.send(address, buffer, callback);
					}
					
					@Override
					public void close() {
						connecter.close();
					}
				};
			}
		};
	}
	
	private TelnetClient() {
	}
}
