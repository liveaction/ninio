package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.NinioProvider;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.core.supervision.tracking.RequestTracker;
import com.davfx.ninio.core.supervision.tracking.RequestTrackerManager;
import com.davfx.ninio.snmp.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static com.davfx.ninio.snmp.AuthCache.AUTH_ENGINES_CACHE_DURATION;

public final class SnmpClient implements SnmpConnecter {

	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpClient.class);

	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(SnmpClient.class.getPackage().getName());

	private final static RequestTracker AUTH_TRACKER_OUT = RequestTrackerManager.instance().getTracker("AUTH", "V2", "OUT");

	public static final int DEFAULT_PORT = 161;

	public static final int DEFAULT_TRAP_PORT = 162;

	public  static final int BULK_SIZE = CONFIG.getInt("bulkSize");

	public static interface Builder extends NinioBuilder<SnmpConnecter> {
		@Deprecated
		Builder with(Executor executor);

		Builder with(NinioBuilder<Connecter> connecterFactory);
	}

	public static Builder builder() {
		return new Builder() {
			private NinioBuilder<Connecter> connecterFactory = UdpSocket.builder();

			@Deprecated
			@Override
			public Builder with(Executor executor) {
				return this;
			}

			@Override
			public Builder with(NinioBuilder<Connecter> connecterFactory) {
				this.connecterFactory = connecterFactory;
				return this;
			}

			@Override
			public SnmpConnecter create(NinioProvider ninioProvider) {
				return new SnmpClient(ninioProvider.executor(), connecterFactory.create(ninioProvider));
			}
		};
	}

	private final Executor executor;

	private final Connecter connecter;

	private final InstanceMapper instanceMapper;

	private final RequestIdProvider requestIdProvider = new RequestIdProvider();

	private final AuthCache authCache;

	private SnmpClient(Executor executor, Connecter connecter) {
		this.executor = executor;
		this.connecter = connecter;
		this.authCache = AuthCache.get();
		instanceMapper = new InstanceMapper(requestIdProvider);
	}

	@Override
	public SnmpRequestBuilder request() {
		return new SnmpRequestBuilder() {
			private String community = null;
			private AuthRemoteSpecification authRemoteSpecification = null;
			private Address address;
			private Oid oid;
			private List<SnmpResult> trap = null;

			@Override
			public SnmpRequestBuilder community(String community) {
				this.community = community;
				return this;
			}
			@Override
			public SnmpRequestBuilder auth(AuthRemoteSpecification authRemoteSpecification) {
				this.authRemoteSpecification = authRemoteSpecification;
				return this;
			}

			private Instance instance = null;

			@Override
			public SnmpRequestBuilder build(Address address, Oid oid) {
				this.address = address;
				this.oid = oid;
				return this;
			}

			@Override
			public void cancel() {
				// Deprecated
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (instance != null) {
							instance.cancel();
						}
					}
				});
			}

			@Override
			public SnmpRequestBuilder add(Oid oid, String value) {
				if (trap == null) {
					trap = new LinkedList<>();
				}
				trap.add(new SnmpResult(oid, value));
				return this;
			}

			@Override
			public Cancelable call(final SnmpCallType type, final SnmpReceiver r) {
				final Auth auth = (authRemoteSpecification == null) ? null : new Auth(authRemoteSpecification.login, authRemoteSpecification.authPassword, authRemoteSpecification.authDigestAlgorithm, authRemoteSpecification.privPassword, authRemoteSpecification.privEncryptionAlgorithm);;
				final String contextName = (authRemoteSpecification == null) ? null : authRemoteSpecification.contextName;
				final Oid o = oid;
				final Address a = address;
				final String c = community;
				final Iterable<SnmpResult> t = (trap == null) ? null : ImmutableList.copyOf(trap);
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (instance != null) {
							throw new IllegalStateException();
						}

						instance = new Instance(connecter, instanceMapper, o, contextName, a, type, c, t);

						AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager = null;
						if (auth != null) {
							Auth previousAuth = authCache.auths.getIfPresent(a);
							if (previousAuth != null) {
								if (!previousAuth.equals(auth)) {
									LOGGER.debug("Auth changed ({} -> {}) for {}", previousAuth, auth, a);
								}
							}
							authCache.auths.put(a, auth);

							EncryptionEngineKey encryptionEngineKey = new EncryptionEngineKey(auth.authDigestAlgorithm, auth.privEncryptionAlgorithm);
							EncryptionEngine encryptionEngine = authCache.encryptionEngines.getIfPresent(encryptionEngineKey);
							if (encryptionEngine == null) {
								encryptionEngine = new EncryptionEngine(auth.authDigestAlgorithm, auth.privEncryptionAlgorithm, AUTH_ENGINES_CACHE_DURATION);
								authCache.encryptionEngines.put(encryptionEngineKey, encryptionEngine);
							}

							AuthRemoteEngineKey authRemoteEngineKey = new AuthRemoteEngineKey(a, auth);
							authRemoteEnginePendingRequestManager = authCache.authRemoteEngines.getIfPresent(authRemoteEngineKey);
							if (authRemoteEnginePendingRequestManager == null) {
								authRemoteEnginePendingRequestManager = new AuthRemoteEnginePendingRequestManager(auth, encryptionEngine);
								authCache.authRemoteEngines.put(authRemoteEngineKey, authRemoteEnginePendingRequestManager);

								authRemoteEnginePendingRequestManager.discoverIfNecessary(a, connecter);
							}
						}

						instance.receiver = r;
						instance.authRemoteEnginePendingRequestManager = authRemoteEnginePendingRequestManager;
						instance.launch();
					}
				});
				return new Cancelable() {
					@Override
					public void cancel() {
						executor.execute(new Runnable() {
							@Override
							public void run() {
								if (instance != null) {
									instance.cancel();
								}
							}
						});
					}
				};
			}
		};
	}
	@Override
	public void connect(final SnmpConnection callback) {
		connecter.connect(new Connection() {
			@Override
			public void received(final Address address, final ByteBuffer buffer) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						LOGGER.trace("Received SNMP packet, size = {}", buffer.remaining());
						int instanceId;
						int errorStatus;
						int errorIndex;
						Iterable<SnmpResult> results;

						Auth auth = authCache.auths.getIfPresent(address);

						AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager;
						if (auth == null) {
							authRemoteEnginePendingRequestManager = null;
						} else {
							AuthRemoteEngineKey authRemoteEngineKey = new AuthRemoteEngineKey(address, auth);
							authRemoteEnginePendingRequestManager = authCache.authRemoteEngines.getIfPresent(authRemoteEngineKey);
						}

						boolean ready;
						if (authRemoteEnginePendingRequestManager != null) {
							ready = authRemoteEnginePendingRequestManager.isReady();
						} else {
							ready = true;
						}
						try {
							SnmpPacketParser parser = new SnmpPacketParser(address, (authRemoteEnginePendingRequestManager == null) ? null : authRemoteEnginePendingRequestManager.engine, buffer);
							instanceId = parser.getRequestId();
							errorStatus = parser.getErrorStatus();
							errorIndex = parser.getErrorIndex();
							results = parser.getResults();
						} catch (Exception e) {
							LOGGER.error("Invalid packet", e);
							return;
						}

						if (authRemoteEnginePendingRequestManager != null) {
							if (ready && (errorStatus == BerConstants.ERROR_STATUS_AUTHENTICATION_NOT_SYNCED)) {
								authRemoteEnginePendingRequestManager.reset();
							}

							authRemoteEnginePendingRequestManager.discoverIfNecessary(address, connecter);
							authRemoteEnginePendingRequestManager.sendPendingRequestsIfReady(address, connecter);
						}

						instanceMapper.handle(address, instanceId, errorStatus, errorIndex, results);
					}
				});
			}

			@Override
			public void failed(final IOException ioe) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						instanceMapper.fail(ioe);
					}
				});

				if (callback != null) {
					callback.failed(ioe);
				}
			}

			@Override
			public void connected(Address address) {
				if (callback != null) {
					callback.connected(address);
				}
			}

			@Override
			public void closed() {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						instanceMapper.fail(new IOException("Closed"));
					}
				});

				if (callback != null) {
					callback.closed();
				}
			}
		});
	}

	@Override
	public void close() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				instanceMapper.close();
			}
		});

		connecter.close();
	}

	private static final class InstanceMapper {
		private final RequestIdProvider requestIdProvider;
		private final Map<Integer, Instance> instances = new HashMap<>();

		public InstanceMapper(RequestIdProvider requestIdProvider) {
			this.requestIdProvider = requestIdProvider;
		}

		public void map(Instance instance) {
			instances.remove(instance.instanceId);

			int instanceId = requestIdProvider.get();

			if (instances.containsKey(instanceId)) {
				LOGGER.warn("The maximum number of simultaneous request has been reached");
				return;
			}

			instances.put(instanceId, instance);

			LOGGER.trace("New instance ID = {}", instanceId);
			instance.instanceId = instanceId;
		}

		public void unmap(Instance instance) {
			instances.remove(instance.instanceId);
			instance.instanceId = RequestIdProvider.IGNORE_ID;
		}

		public void close() {
			for (Instance i : instances.values()) {
				i.close();
			}
			instances.clear();
		}

		public void fail(IOException ioe) {
			for (Instance i : instances.values()) {
				i.fail(ioe);
			}
			instances.clear();
		}

		public void handle(Address address, int instanceId, int errorStatus, int errorIndex, Iterable<SnmpResult> results) {
			if (instanceId == Integer.MAX_VALUE) {
				LOGGER.trace("Calling all instances for address {} (request ID = {})", address, Integer.MAX_VALUE);
				List<Instance> l = new LinkedList<>();
				Iterator<Instance> iterator = instances.values().iterator();
				while (iterator.hasNext()) {
					Instance instance = iterator.next();
					if(instance.address.equals(address)){
						l.add(instance);
						iterator.remove();
					}
				}
				for (Instance i : l) {
					i.handle(errorStatus, errorIndex, results);
				}
				return;
			}

			Instance i = instances.remove(instanceId);
			if (i == null) {
				return;
			}
			i.handle(errorStatus, errorIndex, results);
		}
	}

	private static final class Instance {
		private final Connecter connector;
		private final InstanceMapper instanceMapper;

		private SnmpReceiver receiver;

		private final Oid requestOid;
		private final String requestContextName;
		public int instanceId = RequestIdProvider.IGNORE_ID;

		private final Address address;
		private final String community;
		private final SnmpCallType snmpCallType;
		private AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager = null;

		private final Iterable<SnmpResult> trap;

		public Instance(Connecter connector, InstanceMapper instanceMapper, Oid requestOid, String requestContextName, Address address, SnmpCallType snmpCallType, String community, Iterable<SnmpResult> trap) {
			this.connector = connector;
			this.instanceMapper = instanceMapper;

			this.requestOid = requestOid;
			this.requestContextName = requestContextName;

			this.address = address;
			this.snmpCallType = snmpCallType;
			this.community = community;

			this.trap = trap;
		}

		public void launch() {
			if (receiver != null) {
				instanceMapper.map(this);
			}
			write();
		}

		public void close() {
			receiver = null;
		}

		public void cancel() {
			if (authRemoteEnginePendingRequestManager != null) {
				authRemoteEnginePendingRequestManager.clearPendingRequests();
			}
			instanceMapper.unmap(this);
			receiver = null;
		}

		private void write() {
			SendCallback sendCallback = new SendCallback() {
				@Override
				public void sent() {
				}
				@Override
				public void failed(IOException ioe) {
					fail(ioe);
				}
			};

			if (authRemoteEnginePendingRequestManager == null) {
                AUTH_TRACKER_OUT.track(Address.ipToString(address.ip), v -> String.format("Writing %s v2: %s:%s", snmpCallType, v, requestOid));
				switch (snmpCallType) {
					case GET: {
						Version2cPacketBuilder builder = Version2cPacketBuilder.get(community, instanceId, requestOid);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GET: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
						connector.send(address, b, sendCallback);
						break;
					}
					case GETNEXT: {
						Version2cPacketBuilder builder = Version2cPacketBuilder.getNext(community, instanceId, requestOid);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GETNEXT: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
						connector.send(address, b, sendCallback);
						break;
					}
					case GETBULK: {
						Version2cPacketBuilder builder = Version2cPacketBuilder.getBulk(community, instanceId, requestOid, BULK_SIZE);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GETBULK: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
						connector.send(address, b, sendCallback);
						break;
					}
					case TRAP: {
						Version2cPacketBuilder builder = Version2cPacketBuilder.trap(community, instanceId, requestOid, trap);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing TRAP: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
						connector.send(address, b, sendCallback);
						break;
					}
					default:
						break;
				}
			} else {
				authRemoteEnginePendingRequestManager.registerPendingRequest(new AuthRemoteEnginePendingRequestManager.PendingRequest(snmpCallType, instanceId, requestOid, requestContextName, /*trap, */sendCallback));
				authRemoteEnginePendingRequestManager.discoverIfNecessary(address, connector);
				authRemoteEnginePendingRequestManager.sendPendingRequestsIfReady(address, connector);
			}
		}

		public void fail(IOException e) {
			if (receiver != null) {
				receiver.failed(e);
			}
			receiver = null;
		}

		private void handle(int errorStatus, int errorIndex, Iterable<SnmpResult> results) {
			if (requestOid == null) {
				return;
			}

			if (errorStatus == BerConstants.ERROR_STATUS_AUTHENTICATION_NOT_SYNCED) {
				fail(new IOException("Authentication engine not synced"));
				return;
			}

			if (errorStatus == BerConstants.ERROR_STATUS_AUTHENTICATION_FAILED) {
				fail(new IOException("Authentication failed"));
				return;
			}

			if (errorStatus == BerConstants.ERROR_STATUS_TIMEOUT) {
				fail(new IOException("Timeout"));
				return;
			}

			if (errorStatus != 0) {
				LOGGER.trace("Received error: {}/{}", errorStatus, errorIndex);
			}

			for (SnmpResult r : results) {
				if (r.value == null) {
					continue;
				}
				LOGGER.trace("Addind to results: {}", r);
				if (receiver != null) {
					receiver.received(r);
				}
			}
			if (receiver != null) {
				receiver.finished();
			}
			receiver = null;
		}
	}
}
