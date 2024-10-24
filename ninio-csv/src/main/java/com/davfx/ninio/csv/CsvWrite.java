package com.davfx.ninio.csv;

import com.davfx.ninio.csv.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class CsvWrite {

	private static final String UTF8_BOM = "\uFEFF";
	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(CsvWrite.class.getPackage().getName());
	private static final Charset DEFAULT_CHARSET = Charset.forName(CONFIG.getString("charset"));
	private static final char DEFAULT_DELIMITER = ConfigUtils.getChar(CONFIG, "delimiter");
	private static final char DEFAULT_QUOTE = ConfigUtils.getChar(CONFIG, "quote");
	private static final boolean DEFAULT_ENFORCE_UTF8_BOM = CONFIG.getBoolean("enforceUtf8Bom");

	private Charset charset = DEFAULT_CHARSET;
	private char delimiter = DEFAULT_DELIMITER;
	private char quote = DEFAULT_QUOTE;
	private boolean enforceUtf8Bom = DEFAULT_ENFORCE_UTF8_BOM;
	
	public CsvWrite() {
	}
	
	public CsvWrite withDelimiter(char delimiter) {
		this.delimiter = delimiter;
		return this;
	}
	public CsvWrite withQuote(char quote) {
		this.quote = quote;
		return this;
	}
	public CsvWrite withCharset(Charset charset) {
		this.charset = charset;
		return this;
	}
	public CsvWrite enforceUtf8Bom(boolean enforce) {
		this.enforceUtf8Bom = enforce;
		return this;
	}

	public MayAutoCloseCsvWriter to(final OutputStream out)  {
		final OutputStream prefixedOutputStream = outputStream(out);
		final CsvWriter csvWriter = new CsvWriterImpl(charset, delimiter, quote, prefixedOutputStream);
		return new MayAutoCloseCsvWriter() {
			@Override
			public Line line() throws IOException {
				return csvWriter.line();
			}

			@Override
			public void flush() throws IOException {
				csvWriter.flush();
			}
			
			@Override
			public AutoCloseableCsvWriter autoClose() {
				return new AutoCloseableCsvWriter() {
					
					@Override
					public Line line() throws IOException {
						return csvWriter.line();
					}
					
					@Override
					public void flush() throws IOException {
						csvWriter.flush();
					}
					
					@Override
					public void close() throws IOException {
						try {
							csvWriter.flush();
						} finally {
							out.close();
						}
					}
				};
			}
		};
	}
	
	public AutoCloseableCsvWriter to(File file) throws IOException {
		return to(new FileOutputStream(file)).autoClose();
	}
	public AutoCloseableCsvWriter append(File file) throws IOException {
		return to(new FileOutputStream(file, true)).autoClose();
	}

	private OutputStream outputStream(OutputStream out) {
		return (charset.equals(StandardCharsets.UTF_8) && enforceUtf8Bom)
				? new PrefixedOutputStream(out, UTF8_BOM)
				: out;
	}
	
	private static final class CsvWriterImpl implements CsvWriter {
		private static String encode(char quote, String s) {
			return s.replace(String.valueOf(quote), quote + "" + quote);
		}

		public static final class InnerLine implements Line {
			private final char delimiter;
			private final char quote;
			private final Writer writer;
			private boolean beginning = true;

			public InnerLine(char delimiter, char quote, Writer writer) {
				this.writer = writer;
				this.delimiter = delimiter;
				this.quote = quote;
			}

			@Override
			public Line append(String s) throws IOException {
				if (beginning) {
					beginning = false;
				} else {
					writer.write(delimiter);
				}

				if (s != null) {
					boolean wrap = (s.length() > 0) && ((s.indexOf(quote) >= 0) || (s.indexOf(delimiter) >= 0) || (s.indexOf('\n') >= 0));
					if (wrap) {
						writer.write(quote);
						writer.write(encode(quote, s));
						writer.write(quote);
					} else {
						writer.write(s);
					}
				}
				return this;
			}
			
			@Override
			public void close() throws IOException {
				writer.write('\n');
			}
		}

		private final char delimiter;
		private final char quote;
		private final Writer writer;
		
		public CsvWriterImpl(Charset charset, char delimiter, char quote, OutputStream out) {
			this.delimiter = delimiter;
			this.quote = quote;
			this.writer = new OutputStreamWriter(out, charset);
		}

		@Override
		public Line line() throws IOException {
			return new InnerLine(delimiter, quote, writer);
		}
		
		@Override
		public void flush() throws IOException {
			writer.flush();
		}
	}

	private static class PrefixedOutputStream extends OutputStream {

		private boolean firstWrite = true;
		private final OutputStream out;
		private final String prefix;

		public PrefixedOutputStream(OutputStream out, String prefix) {
			this.out = out;
			this.prefix = prefix;
		}

		@Override
		public void write(int b) throws IOException {
			prefixOnFirstWrite();
			out.write(b);
		}
		@Override
		public void write(byte[] b) throws IOException {
			prefixOnFirstWrite();
			out.write(b);
		}
		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			prefixOnFirstWrite();
			out.write(b, off, len);
		}

		private void prefixOnFirstWrite() throws IOException {
			if (!firstWrite) {
				return;
			}
			firstWrite = false;
			out.write(prefix.getBytes());
		}

		@Override
		public void flush() throws IOException {
			out.flush();
		}
		@Override
		public void close() throws IOException {
			out.close();
		}
	}
}
