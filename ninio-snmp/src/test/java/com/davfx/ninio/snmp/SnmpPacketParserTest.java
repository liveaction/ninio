package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.snmp.encryption.AuthProtocol;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class SnmpPacketParserTest {

	private static final String PACKET_FIXTURE_PATH = "/snmp/autorizationErrorSnmpPacket.raw";
	private static final String AUTH_LOGIN = "entreprise_user";
	private static final String AUTH_PASSWORD = "somepassword";
	private static final String AUTH_DIGEST_ALGORITHM = "SHA";

	@Test
	public void testAuthorizationErrorCanActuallyBeParsed() throws Exception {
		ByteBuffer fileBuffer = readFixtureAsByteBuffer();
		SnmpPacketParser parser = new SnmpPacketParser(new Address(Address.LOCALHOST, 161), createAuthRemoteEngine(), fileBuffer);

		Assertions.assertThat(parser.getRequestId()).isEqualTo(1940283436);
		Assertions.assertThat(parser.getErrorStatus()).isEqualTo(16);
		Assertions.assertThat(parser.getErrorIndex()).isEqualTo(1);
		Assertions.assertThat(toList(parser.getResults())).isEmpty();
	}

	private static ByteBuffer readFixtureAsByteBuffer() throws Exception {
		try (InputStream inputStream = SnmpPacketParserTest.class.getResourceAsStream(PACKET_FIXTURE_PATH)) {
			Assertions.assertThat(inputStream)
				.as("Missing fixture file: src/test/resources%s", PACKET_FIXTURE_PATH)
				.isNotNull();
			return ByteBuffer.wrap(inputStream.readAllBytes());
		}
	}

	private static AuthRemoteEngine createAuthRemoteEngine() {
		Auth auth = new Auth(AUTH_LOGIN, AUTH_PASSWORD, AUTH_DIGEST_ALGORITHM, null, null);
		EncryptionEngine encryptionEngine = new EncryptionEngine(AuthProtocol.fromAlgorithm(AUTH_DIGEST_ALGORITHM), null, 60d);
		return new AuthRemoteEngine(auth, encryptionEngine);
	}

	private static List<SnmpResult> toList(Iterable<SnmpResult> results) {
		List<SnmpResult> list = new ArrayList<>();
		for (SnmpResult result : results) {
			list.add(result);
		}
		return list;
	}
}
