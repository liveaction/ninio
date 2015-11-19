package com.davfx.ninio.http.util;

import java.io.OutputStream;

import com.davfx.ninio.http.HttpContentType;
import com.davfx.ninio.http.HttpMessage;
import com.davfx.ninio.http.HttpStatus;

public interface HttpController {
	interface HttpStream {
		void produce(OutputStream output) throws Exception;
	}
	interface HttpWrap {
		void handle(Http http) throws Exception;
	}
	
	final class Http {
		final int status;
		final String reason;
		String contentType = HttpContentType.plainText();
		long contentLength = -1L;
		String content = null;
		HttpStream stream = null;
		final HttpWrap wrap;
		
		private Http(int status, String reason) {
			this.status = status;
			this.reason = reason;
			wrap = null;
		}
		private Http(int status, String reason, HttpWrap wrap) {
			this.status = status;
			this.reason = reason;
			this.wrap = wrap;
		}
		
		public Http contentType(String contentType) {
			this.contentType = contentType;
			return this;
		}
		public Http contentLength(long contentLength) {
			this.contentLength = contentLength;
			return this;
		}
		
		public Http content(String content) {
			this.content = content;
			stream = null;
			return this;
		}
		public String content() {
			return content;
		}
		public Http stream(HttpStream stream) {
			content = null;
			this.stream = stream;
			return this;
		}
		
		public static Http ok() {
			return new Http(HttpStatus.OK, HttpMessage.OK);
		}
		public static Http internalServerError() {
			return new Http(HttpStatus.INTERNAL_SERVER_ERROR, HttpMessage.INTERNAL_SERVER_ERROR);
		}
		public static Http notFound() {
			return new Http(HttpStatus.NOT_FOUND, HttpMessage.NOT_FOUND);
		}
		
		public static Http wrap(HttpWrap wrap) {
			return new Http(HttpStatus.OK, HttpMessage.OK, wrap);
		}


	}
}
