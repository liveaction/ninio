package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.QueueReady;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.util.ClassThreadFactory;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.Pair;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class ProxyServer implements AutoCloseable, Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyServer.class);
	
	private static final Config CONFIG = ConfigFactory.load(ProxyServer.class.getClassLoader());

	public static final double READ_TIMEOUT = ConfigUtils.getDuration(CONFIG, "ninio.proxy.timeout.read");
	
	private final Queue queue;
	private final ServerSide proxyServerSide;
	private final ExecutorService listenExecutor;
	private final ExecutorService clientExecutor;
	private final Set<String> hostsToFilter = new HashSet<>();
	private final ServerSocket serverSocket;
	
	public ProxyServer(int port, int maxNumberOfSimultaneousClients) throws IOException {
		queue = new Queue();
		proxyServerSide = new BaseServerSide(queue);
		LOGGER.debug("Proxy server on port {}", port);
		listenExecutor = Executors.newSingleThreadExecutor(new ClassThreadFactory(ProxyServer.class, "listen"));
		clientExecutor = Executors.newFixedThreadPool(maxNumberOfSimultaneousClients, new ClassThreadFactory(ProxyServer.class, "read"));
		serverSocket = new ServerSocket(port);
	}

	@Override
	public void close() {
		listenExecutor.shutdown();
		clientExecutor.shutdown();
		try {
			serverSocket.close();
		} catch (IOException ioe) {
		}
	}
	
	public ProxyServer override(String type, ServerSideConfigurator configurator) {
		proxyServerSide.override(type, configurator);
		return this;
	}
	
	public ProxyServer filter(String host) {
		LOGGER.debug("Will filter out: {}", host);
		hostsToFilter.add(host);
		return this;
	}
	
	public ProxyServer start() {
		listenExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					while (true) {
						final Socket socket = serverSocket.accept();
						try {
							socket.setKeepAlive(true);
							socket.setSoTimeout((int) (READ_TIMEOUT * 1000d));
						} catch (IOException e) {
							LOGGER.error("Could not configure client socket", e);
						}
	
						clientExecutor.execute(new Runnable() {
							@Override
							public void run() {
								final Map<Integer, Pair<Address, CloseableByteBufferHandler>> establishedConnections = new HashMap<>();
								final boolean[] closed = new boolean[] { false };
	
								try {
									final DataOutputStream out = new DataOutputStream(socket.getOutputStream());
									DataInputStream in = new DataInputStream(socket.getInputStream());
									LOGGER.debug("Accepted connection from: {}", socket.getInetAddress());
	
									while (true) {
										LOGGER.trace("Server waiting for connection ID");
										final int connectionId = in.readInt();
										int len = in.readInt();
										if (len < 0) {
											int command = -len;
											if (command == ProxyCommons.Commands.ESTABLISH_CONNECTION) {
												final Address address = new Address(in.readUTF(), in.readInt());
												ReadyFactory factory = proxyServerSide.read(in);
												Ready r = new QueueReady(queue, factory.create(queue));
												r.connect(address, new ReadyConnection() {
													@Override
													public void failed(IOException e) {
														LOGGER.warn("Could not connect to {}", address, e);
														try {
															out.writeInt(connectionId);
															out.writeInt(-ProxyCommons.Commands.FAIL_CONNECTION);
															out.flush();
														} catch (IOException ioe) {
															try {
																out.close();
															} catch (IOException se) {
															}
														}
													}
													
													@Override
													public void connected(FailableCloseableByteBufferHandler write) {
														synchronized (establishedConnections) {
															if (closed[0]) {
																write.close();
																return;
															}
															establishedConnections.put(connectionId, new Pair<Address, CloseableByteBufferHandler>(address, write));
														}
	
														try {
															out.writeInt(connectionId);
															out.writeInt(-ProxyCommons.Commands.ESTABLISH_CONNECTION);
															out.flush();
														} catch (IOException ioe) {
															try {
																out.close();
															} catch (IOException se) {
															}
														}
													}
													
													@Override
													public void close() {
														try {
															out.writeInt(connectionId);
															out.writeInt(0);
															out.flush();
														} catch (IOException ioe) {
															try {
																out.close();
															} catch (IOException se) {
															}
														}
													}
													
													@Override
													public void handle(Address address, ByteBuffer buffer) {
														if (!buffer.hasRemaining()) {
															return;
														}
														try {
															out.writeInt(connectionId);
															out.writeInt(buffer.remaining());
															out.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
															out.flush();
														} catch (IOException ioe) {
															try {
																out.close();
															} catch (IOException se) {
															}
														}
														buffer.position(buffer.position() + buffer.remaining());
													}
												});
											}
										} else if (len == 0) {
											Pair<Address, CloseableByteBufferHandler> connection;
											synchronized (establishedConnections) {
												connection = establishedConnections.remove(connectionId);
											}
											if (connection != null) {
												if (connection.second != null) {
													connection.second.close();
												}
											}
										} else {
											byte[] b = new byte[len];
											in.readFully(b);
											Pair<Address, CloseableByteBufferHandler> connection;
											synchronized (establishedConnections) {
												connection = establishedConnections.get(connectionId);
											}
											if (connection != null) {
												if (!hostsToFilter.contains(connection.first.getHost())) {
													if (connection.second != null) {
														connection.second.handle(connection.first, ByteBuffer.wrap(b));
													}
												}
											}
										}
									}
								} catch (IOException e) {
									LOGGER.info("Socket closed by peer: {}", e.getMessage());
	
									try {
										socket.close();
									} catch (IOException ioe) {
									}
									
									List<CloseableByteBufferHandler> toClose = new LinkedList<>();
									synchronized (establishedConnections) {
										closed[0] = true;
										for (Pair<Address, CloseableByteBufferHandler> connection : establishedConnections.values()) {
											if (connection.second != null) {
												toClose.add(connection.second);
											}
										}
									}
									for (CloseableByteBufferHandler c : toClose) {
										c.close();
									}
								}
							}
						});
					}
				} catch (IOException se) {
					LOGGER.debug("Server socket closed: {}", se.getMessage());
				}
			}
		});
		return this;
	}
}
