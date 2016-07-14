package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;
import java.util.zip.Deflater;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.NopConnecterConnectingCallback;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

final class ZlibCompressingConnector implements Connecter.Connecting {
	private static final Config CONFIG = ConfigUtils.load(ZlibCompressingConnector.class);
	private static final int BUFFER_SIZE = CONFIG.getBytes("zlib.buffer").intValue();

	private final Connecter.Connecting wrappee;
	private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
	private boolean activated = false;

	public ZlibCompressingConnector(Connecter.Connecting wrappee) {
		this.wrappee = wrappee;
	}

	public void init() {
		activated = true;
	}

	@Override
	public void send(Address address, ByteBuffer buffer, Callback callback) {
		if (!activated) {
			wrappee.send(address, buffer, callback);
			return;
		}
		deflater.setInput(buffer.array(), buffer.position(), buffer.remaining());
		buffer.position(buffer.limit());
		write(address, callback);
	}

	@Override
	public void close() {
		wrappee.close();
	}

	private void write(Address address, Callback callback) {
		ByteBuffer toSend = null;
		
		while (true) {
			int offset = SshSpecification.OPTIMIZATION_SPACE;
			ByteBuffer deflated = ByteBuffer.allocate(BUFFER_SIZE);
			deflated.position(offset);
			int c = deflater.deflate(deflated.array(), deflated.position(), deflated.remaining(), Deflater.SYNC_FLUSH);
			
			if (c == 0) {
				break;
			}
			
			deflated.position(deflated.position() + c);
			deflated.flip();
			deflated.position(offset);
			
			if (toSend != null) {
				wrappee.send(address, toSend, new NopConnecterConnectingCallback());
			}
			toSend = deflated;
		}
		
		if (toSend != null) {
			wrappee.send(address, toSend, callback);
		} else {
			callback.sent();
		}
	}
}
