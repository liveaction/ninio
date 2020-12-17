package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.InMemoryCache;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import static com.davfx.ninio.snmp.TestUtil.findAvailablePort;

public class SnmpTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpTest.class);

	private int port;

	@Before
	public void setUp() throws Exception {
		port = findAvailablePort();
	}

	@Test
	public void test() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			TreeMap<Oid, String> map = new TreeMap<>();
			map.put(new Oid("1.1.1"), "val1.1.1");
			map.put(new Oid("1.1.1.1"), "val1.1.1.1");
			map.put(new Oid("1.1.1.2"), "val1.1.1.2");
			map.put(new Oid("1.1.2"), "val1.1.2");
			map.put(new Oid("1.1.3.1"), "val1.1.3.1");
			map.put(new Oid("1.1.3.2"), "val1.1.3.2");
			
			final Wait waitServer = new Wait();
			try (Disconnectable snmpServer = ninio.create(SnmpServer.builder().with(UdpSocket.builder().bind(new Address(Address.LOCALHOST, port)))
					.handle(new FromMapSnmpServerHandler(map, new SnmpServerHandler() {
						@Override
						public void from(Oid oid, Callback callback) {
						}
						@Override
						public void failed(IOException ioe) {
						}
						@Override
						public void connected(Address address) {
						}
						@Override
						public void closed() {
							waitServer.run();
						}
					})))) {
				final Wait waitClient = new Wait();
				try (SnmpConnecter snmpClient = ninio.create(SnmpClient.builder().with(UdpSocket.builder()))) {
					snmpClient.connect(new SnmpConnection() {
							@Override
							public void failed(IOException ioe) {
							}
							@Override
							public void connected(Address address) {
							}
							@Override
							public void closed() {
								waitClient.run();
							}
						});
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1]");
					Assertions.assertThat(getbulk(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1")).toString()).isEqualTo("[1.1.1:val1.1.1, 1.1.1.1:val1.1.1.1, 1.1.1.2:val1.1.1.2, 1.1.2:val1.1.2, 1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.2")).toString()).isEqualTo("[1.1.2:val1.1.2]");
					Assertions.assertThat(getbulk(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.3")).toString()).isEqualTo("[1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.4")).toString()).isEqualTo("[]");
				}
				waitClient.waitFor();
			}
			waitServer.waitFor();
		}
	}

    //    @Test
//    public void testDESNinio() throws UnknownHostException, InterruptedException {
//        try (Ninio ninio = Ninio.create()) {
//            try (SnmpConnecter snmpClient = SnmpTimeout.wrap(20d, ninio.create(SnmpClient.builder().with(UdpSocket.builder())))) {
//                snmpClient.connect(new SnmpConnection() {
//                    @Override
//                    public void failed(IOException ioe) {
//                    }
//
//                    @Override
//                    public void connected(Address address) {
//                    }
//
//                    @Override
//                    public void closed() {
//                    }
//                });
//
//                final CountDownLatch latch = new CountDownLatch(1);
//                final AtomicBoolean res = new AtomicBoolean(false);
//                snmpClient.request().auth(new AuthRemoteSpecification("usr-md5-des", "authkey1", "MD5", "privkey1", "DES", ""))
//                        .build(new Address(InetAddress.getByName("10.3.33.24").getAddress(), 9223), new Oid("1.3.6.1.2.1.2.2.1.2"))
//                        .call(SnmpCallType.GETBULK, new SnmpReceiver() {
//                            @Override
//                            public void received(SnmpResult result) {
//                                res.set(true);
//                                System.out.println("success -> " + result);
//                            }
//
//                            @Override
//                            public void finished() {
//                                latch.countDown();
//                            }
//
//                            @Override
//                            public void failed(IOException ioe) {
//                                res.set(false);
//                                latch.countDown();
//                                ioe.printStackTrace();
//                            }
//                        });
//                latch.await();
//                Assertions.assertThat(res.get()).isTrue();
//            }
//        }
//    }
//
//    static {
//        LogFactory.setLogFactory(new ConsoleLogFactory());
//        ConsoleLogAdapter.setDebugEnabled(true);
//    }
//
//    @Test
//    public void testDESSnmp4j() throws Exception {
//
//        org.snmp4j.smi.Address targetAddress = GenericAddress.parse("10.3.33.24/9223");
//        TransportMapping transport = new DefaultUdpTransportMapping();
//        Snmp snmp = new Snmp(transport);
//        SecurityProtocols instance = SecurityProtocols.getInstance();
//        instance.addAuthenticationProtocol(new AuthSHA());
//        instance.addAuthenticationProtocol(new AuthMD5());
//        USM usm = new USM(instance,
//                new OctetString(MPv3.createLocalEngineID()), 3);
//        SecurityModels.getInstance().addSecurityModel(usm);
//        transport.listen();
//        // add user to the USM
//        snmp.getUSM().addUser(new UsmUser(new OctetString("usr-md5-des"),
//                        AuthMD5.ID,
//                        new OctetString("authkey1"),
//                        PrivDES.ID,
//                        new OctetString("privkey1")));
//        // create the target
//        UserTarget target = new UserTarget();
//        target.setAddress(targetAddress);
//        target.setRetries(1);
//        target.setTimeout(5000);
//        target.setVersion(SnmpConstants.version3);
//        target.setSecurityLevel(SecurityLevel.AUTH_PRIV);
//        target.setSecurityName(new OctetString("usr-md5-des"));
//
//        // create the PDU
//        PDU pdu = new ScopedPDU();
//        pdu.add(new VariableBinding(new OID("1.3.6.1.2.1.2.2.1.2.1")));
//        pdu.setType(PDU.GET);
//        pdu.setMaxRepetitions(10);
//
//        // send the PDU
//        ResponseEvent response = snmp.send(pdu, target);
//        // extract the response PDU (could be null if timed out)
//        PDU responsePDU = response.getResponse();
//        System.out.println(responsePDU);
//        // extract the address used by the agent to send the response:
//        org.snmp4j.smi.Address peerAddress = response.getPeerAddress();
//    }
	
	private static List<SnmpResult> call(SnmpConnecter snmpClient, Address a, Oid oid, SnmpCallType snmpCallType) throws IOException {
		final Lock<List<SnmpResult>, IOException> lock = new Lock<>();
		snmpClient.request().community("community").build(a, oid).call(snmpCallType, new SnmpReceiver() {
			private final List<SnmpResult> r = new LinkedList<>();
			
			@Override
			public void received(SnmpResult result) {
				r.add(result);
			}
			
			@Override
			public void finished() {
				lock.set(r);
			}
			
			@Override
			public void failed(IOException ioe) {
				lock.fail(ioe);
			}
		});
		return lock.waitFor();
	}
	private static List<SnmpResult> get(SnmpConnecter snmpClient, Address a, Oid oid) throws IOException {
		return call(snmpClient, a, oid, SnmpCallType.GET);
	}
	private static List<SnmpResult> getbulk(SnmpConnecter snmpClient, Address a, Oid oid) throws IOException {
		return call(snmpClient, a, oid, SnmpCallType.GETBULK);
	}

	
	@Test
	public void testTimeout() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			final Lock<String, IOException> lock = new Lock<>();
			try (SnmpConnecter snmpClient = SnmpTimeout.wrap(0.5d, ninio.create(SnmpClient.builder().with(UdpSocket.builder())))) {
				snmpClient.connect(new SnmpConnection() {
						@Override
						public void failed(IOException ioe) {
						}
						@Override
						public void connected(Address address) {
						}
						@Override
						public void closed() {
						}
					});
				
				snmpClient.request().community("community").build(new Address(Address.LOCALHOST, port), new Oid("1.1.1")).call(SnmpCallType.GET, new SnmpReceiver() {
							@Override
							public void received(SnmpResult result) {
							}
							@Override
							public void finished() {
							}
							@Override
							public void failed(IOException ioe) {
								lock.set(ioe.getMessage());
							}
						});

				Assertions.assertThat(lock.waitFor()).isEqualTo("Timeout");
			}
		}
	}
	
	@Test
	public void testWithCache() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			TreeMap<Oid, String> map = new TreeMap<>();
			map.put(new Oid("1.1.1"), "val1.1.1");
			map.put(new Oid("1.1.1.1"), "val1.1.1.1");
			map.put(new Oid("1.1.1.2"), "val1.1.1.2");
			map.put(new Oid("1.1.2"), "val1.1.2");
			map.put(new Oid("1.1.3.1"), "val1.1.3.1");
			map.put(new Oid("1.1.3.2"), "val1.1.3.2");

			final SnmpServerHandler handler = new FromMapSnmpServerHandler(map, new SnmpServerHandler() {
				@Override
				public void from(Oid oid, Callback callback) {
				}
				@Override
				public void failed(IOException ioe) {
				}
				@Override
				public void connected(Address address) {
				}
				@Override
				public void closed() {
				}
			});
			
			final Wait waitServer = new Wait();
			try (Disconnectable snmpServer = ninio.create(SnmpServer.builder().with(UdpSocket.builder().bind(new Address(Address.LOCALHOST, port)))
				.handle(new SnmpServerHandler() {
					private int n = 0;
					private Thread thread = null;
					@Override
					public void from(Oid oid, final Callback callback) {
						if (thread == null) {
							thread = Thread.currentThread();
						} else {
							if (thread != Thread.currentThread()) {
								throw new RuntimeException();
							}
						}
						
						final int k = n;
						LOGGER.debug("******* {} {}", oid, k);
						n++;
						handler.from(oid, new Callback() {
							@Override
							public boolean handle(SnmpResult result) {
								return callback.handle(new SnmpResult(result.oid, result.value + "/" + k));
							}
						});
					}
					@Override
					public void closed() {
						waitServer.run();
					}
					@Override
					public void connected(Address address) {
					}
					@Override
					public void failed(IOException ioe) {
					}
				}))) {
				try (SnmpConnecter snmpClient = ninio.create(SnmpClient.builder().with(InMemoryCache.<Integer>builder().using(new SnmpInMemoryCacheInterpreter())))) {
					snmpClient.connect(new SnmpConnection() {
						@Override
						public void failed(IOException ioe) {
						}
						@Override
						public void connected(Address address) {
						}
						@Override
						public void closed() {
						}
					});
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/0]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/0]");
					Assertions.assertThat(getbulk(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/1, 1.1.1.1:val1.1.1.1/1, 1.1.1.2:val1.1.1.2/1, 1.1.2:val1.1.2/1, 1.1.3.1:val1.1.3.1/1, 1.1.3.2:val1.1.3.2/1]");
					Assertions.assertThat(getbulk(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/1, 1.1.1.1:val1.1.1.1/1, 1.1.1.2:val1.1.1.2/1, 1.1.2:val1.1.2/1, 1.1.3.1:val1.1.3.1/1, 1.1.3.2:val1.1.3.2/1]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.2")).toString()).isEqualTo("[1.1.2:val1.1.2/2]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.2")).toString()).isEqualTo("[1.1.2:val1.1.2/2]");
					Assertions.assertThat(getbulk(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.3")).toString()).isEqualTo("[1.1.3.1:val1.1.3.1/3, 1.1.3.2:val1.1.3.2/3]");
					Assertions.assertThat(getbulk(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.3")).toString()).isEqualTo("[1.1.3.1:val1.1.3.1/3, 1.1.3.2:val1.1.3.2/3]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.4")).toString()).isEqualTo("[]");
				}
			}
			waitServer.waitFor();
		}
	}
	
	@Test
	public void testWithNoCache() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			TreeMap<Oid, String> map = new TreeMap<>();
			map.put(new Oid("1.1.1"), "val1.1.1");
			map.put(new Oid("1.1.1.1"), "val1.1.1.1");
			map.put(new Oid("1.1.1.2"), "val1.1.1.2");
			map.put(new Oid("1.1.2"), "val1.1.2");
			map.put(new Oid("1.1.3.1"), "val1.1.3.1");
			map.put(new Oid("1.1.3.2"), "val1.1.3.2");

			final SnmpServerHandler handler = new FromMapSnmpServerHandler(map, new SnmpServerHandler() {
				@Override
				public void from(Oid oid, Callback callback) {
				}
				@Override
				public void failed(IOException ioe) {
				}
				@Override
				public void connected(Address address) {
				}
				@Override
				public void closed() {
				}
			});
			
			final Wait waitServer = new Wait();
			try (Disconnectable snmpServer = ninio.create(SnmpServer.builder().with(UdpSocket.builder().bind(new Address(Address.LOCALHOST, port)))
					.handle(new SnmpServerHandler() {
						private int n = 0;
						private Thread thread = null;
						@Override
						public void from(Oid oid, final Callback callback) {
							if (thread == null) {
								thread = Thread.currentThread();
							} else {
								if (thread != Thread.currentThread()) {
									throw new RuntimeException();
								}
							}
							
							final int k = n;
							LOGGER.debug("******* {} {}", oid, k);
							n++;
							handler.from(oid, new Callback() {
								@Override
								public boolean handle(SnmpResult result) {
									return callback.handle(new SnmpResult(result.oid, result.value + "/" + k));
								}
							});
						}
						@Override
						public void closed() {
							waitServer.run();
						}
						@Override
						public void connected(Address address) {
						}
						@Override
						public void failed(IOException ioe) {
						}
					}))) {
				final Wait waitClient = new Wait();
				try (SnmpConnecter snmpClient = ninio.create(SnmpClient.builder().with(UdpSocket.builder()))) {
					snmpClient.connect(new SnmpConnection() {
							@Override
							public void failed(IOException ioe) {
							}
							@Override
							public void connected(Address address) {
							}
							@Override
							public void closed() {
								waitClient.run();
							}
						});
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/0]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/1]");
					Assertions.assertThat(getbulk(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/2, 1.1.1.1:val1.1.1.1/2, 1.1.1.2:val1.1.1.2/2, 1.1.2:val1.1.2/2, 1.1.3.1:val1.1.3.1/2, 1.1.3.2:val1.1.3.2/2]");
					Assertions.assertThat(getbulk(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/3, 1.1.1.1:val1.1.1.1/3, 1.1.1.2:val1.1.1.2/3, 1.1.2:val1.1.2/3, 1.1.3.1:val1.1.3.1/3, 1.1.3.2:val1.1.3.2/3]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.2")).toString()).isEqualTo("[1.1.2:val1.1.2/4]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.2")).toString()).isEqualTo("[1.1.2:val1.1.2/5]");
					Assertions.assertThat(getbulk(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.3")).toString()).isEqualTo("[1.1.3.1:val1.1.3.1/6, 1.1.3.2:val1.1.3.2/6]");
					Assertions.assertThat(getbulk(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.3")).toString()).isEqualTo("[1.1.3.1:val1.1.3.1/7, 1.1.3.2:val1.1.3.2/7]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.4")).toString()).isEqualTo("[]");
				}
				waitClient.waitFor();
			}
			waitServer.waitFor();
		}
	}

}
