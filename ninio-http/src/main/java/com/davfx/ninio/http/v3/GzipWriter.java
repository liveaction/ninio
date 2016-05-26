package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

final class GzipWriter implements HttpContentSender {
	private static final Config CONFIG = ConfigFactory.load(GzipReader.class.getClassLoader());
	private static final int BUFFER_SIZE = CONFIG.getBytes("ninio.http.gzip.buffer").intValue();

	private static final int OS_TYPE_UNKNOWN = 0xFF;

	private boolean gzipHeaderWritten = false;
	private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
	private final CRC32 crc = new CRC32();

	private final HttpContentSender wrappee;
	
	private boolean finished = false;
	
	public GzipWriter(HttpContentSender wrappee) {
		this.wrappee = wrappee;
	}

	private ByteBuffer buildGzipFooter() {
		ByteBuffer b = ByteBuffer.allocate(8);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.putInt((int) (crc.getValue() & 0xFFFFFFFFL));
		b.putInt(deflater.getTotalIn());
		b.flip();
		return b;
	}

	private static ByteBuffer buildGzipHeaders() {
		int time = (int) (System.currentTimeMillis() / 1000L);
		ByteBuffer b = ByteBuffer.allocate(10);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.putShort((short) GzipReader.GZIP_MAGIC);
		b.put((byte) Deflater.DEFLATED);
		b.put((byte) 0);
		b.putInt(time);
		b.put((byte) 0);
		b.put((byte) OS_TYPE_UNKNOWN);
		b.flip();
		return b;
	}

	@Override
	public HttpContentSender send(ByteBuffer buffer) {
		if (finished) {
			throw new IllegalStateException();
		}
		deflater.setInput(buffer.array(), buffer.position(), buffer.remaining());
		crc.update(buffer.array(), buffer.position(), buffer.remaining());
		buffer.position(buffer.limit());
		write();
		return this;
	}

	@Override
	public void finish() {
		if (finished) {
			throw new IllegalStateException();
		}
		finished = true;
		deflater.finish();
		write();
		wrappee.send(buildGzipFooter());
		wrappee.finish();
	}

	private void write() {
		if (!gzipHeaderWritten) {
			wrappee.send(buildGzipHeaders());
			gzipHeaderWritten = true;
		}
		while (true) {
			ByteBuffer deflated = ByteBuffer.allocate(BUFFER_SIZE);
			int c = deflater.deflate(deflated.array(), deflated.position(),
					deflated.remaining());
			if (c <= 0) {
				break;
			}
			deflated.position(deflated.position() + c);
			deflated.flip();
			wrappee.send(deflated);
		}
	}
	
	@Override
	public void cancel() {
		finished = true;
		wrappee.cancel();
	}
}