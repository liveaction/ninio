package com.davfx.ninio.http;

import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.http.service.Annotated.Builder;
import com.davfx.ninio.http.service.HttpContentType;
import com.davfx.ninio.http.service.HttpController;
import com.davfx.ninio.http.service.annotations.*;
import com.google.common.base.Charsets;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static com.davfx.ninio.http.TestUtils.findAvailablePort;

public class HttpServiceTest {

	private int port;
	private int routerPort;

	@Before
	public void setUp() throws Exception {
		port = findAvailablePort();
		routerPort = findAvailablePort();
	}

	@Path("/get")
	public static final class TestGetWithQueryParameterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}

	@Test
	public void testGetWithQueryParameter() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, port, new TestUtils.ControllerVisitor(TestGetWithQueryParameterController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/get/hello?message=world")).isEqualTo("text/plain; charset=UTF-8/GET hello:world\n");
			}
		}
	}
	
	@Path("/getpath")
	public static final class TestGetWithPathParameterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello/{message}/a")
		public Http echo(@PathParameter("message") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}

	@Test
	public void testGetWithPathParameter() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, port, new TestUtils.ControllerVisitor(TestGetWithPathParameterController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/getpath/hello/world/a")).isEqualTo("text/plain; charset=UTF-8/GET hello:world\n");
			}
		}
	}
	
	@Path("/post")
	public static final class TestPostWithBodyParameterController implements HttpController {
		@Route(method = HttpMethod.POST, path = "/hello")
		public Http echo(@BodyParameter("message") String message) {
			return Http.ok().content("POST hello:" + message);
		}
	}

	@Test
	public void testPostWithBodyParameter() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, port, new TestUtils.ControllerVisitor(TestPostWithBodyParameterController.class))) {
				Assertions.assertThat(TestUtils.post("http://127.0.0.1:"+port+"/post/hello", "message=world")).isEqualTo("text/plain; charset=UTF-8/POST hello:world\n");
			}
		}
	}

	@Path("/getheader")
	public static final class TestGetWithHeaderController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@HeaderParameter("Host") String host) {
			return Http.ok().content("GET Host:" + host);
		}
	}
	@Test
	public void testGetWithHeader() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, port, new TestUtils.ControllerVisitor(TestGetWithHeaderController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/getheader/hello")).isEqualTo("text/plain; charset=UTF-8/GET Host:127.0.0.1:"+port+"\n");
			}
		}
	}

	@Path("/getwithdefault")
	public static final class TestGetWithQueryParameterDefaultValueController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") @DefaultValue("www") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}

	@Test
	public void testGetWithQueryParameterDefaultValue() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, port, new TestUtils.ControllerVisitor(TestGetWithQueryParameterDefaultValueController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/getwithdefault/hello")).isEqualTo("text/plain; charset=UTF-8/GET hello:www\n");
			}
		}
	}

	@Path("/getfork")
	public static final class TestGetForkController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello/{a}/fork0")
		public Http echo0(@PathParameter("a") String a) {
			return Http.ok().content("GET0 hello:" + a);
		}
		@Route(method = HttpMethod.GET, path = "/hello/{a}/fork1")
		public Http echo1(@PathParameter("a") String a) {
			return Http.ok().content("GET1 hello:" + a);
		}
	}

	@Test
	public void testGetForkParameter() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, port, new TestUtils.ControllerVisitor(TestGetForkController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/getfork/hello/world/fork1")).isEqualTo("text/plain; charset=UTF-8/GET1 hello:world\n");
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/getfork/hello/world/fork0")).isEqualTo("text/plain; charset=UTF-8/GET0 hello:world\n");
			}
		}
	}
	
	@Path("/getparamfork")
	public static final class TestGetForkWithQueryController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello/{a}?fork0")
		public Http echo0(@PathParameter("a") String a) {
			return Http.ok().content("GET0 hello:" + a);
		}
		@Route(method = HttpMethod.GET, path = "/hello/{a}?fork1")
		public Http echo1(@PathParameter("a") String a) {
			return Http.ok().content("GET1 hello:" + a);
		}
		@Route(method = HttpMethod.GET, path = "/hello/{a}?fork2=f")
		public Http echo2(@PathParameter("a") String a) {
			return Http.ok().content("GET2f hello:" + a);
		}
		@Route(method = HttpMethod.GET, path = "/hello/{a}?fork2=g")
		public Http echo3(@PathParameter("a") String a) {
			return Http.ok().content("GET2g hello:" + a);
		}
	}

	@Test
	public void testGetForkWithQueryParameter() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, port, new TestUtils.ControllerVisitor(TestGetForkWithQueryController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/getparamfork/hello/world?fork1")).isEqualTo("text/plain; charset=UTF-8/GET1 hello:world\n");
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/getparamfork/hello/world?fork0")).isEqualTo("text/plain; charset=UTF-8/GET0 hello:world\n");
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/getparamfork/hello/world?fork0=f")).isEqualTo("text/plain; charset=UTF-8/GET0 hello:world\n");
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/getparamfork/hello/world?fork2=f")).isEqualTo("text/plain; charset=UTF-8/GET2f hello:world\n");
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/getparamfork/hello/world?fork2=g")).isEqualTo("text/plain; charset=UTF-8/GET2g hello:world\n");
			}
		}
	}
	
	@Path("/getstream")
	public static final class TestGetStreamController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/{message}/{to}")
		public Http echo(final @PathParameter("message") String message, final @PathParameter("to") String to, final @QueryParameter("n") String n) throws IOException {
			StringBuilder b = new StringBuilder();
			int nn = Integer.parseInt(n);
			for (int i = 0; i < nn; i++) {
				b.append("GET " + message + ":" + to + "\n");
			}
			return Http.ok().contentType(HttpContentType.plainText(Charsets.UTF_8)).stream(new ByteArrayInputStream(b.toString().getBytes(Charsets.UTF_8)));
		}
	}

	@Test
	public void testGetStreamParameter() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, port, new TestUtils.ControllerVisitor(TestGetStreamController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/getstream/hello/world?n=3")).isEqualTo("text/plain; charset=UTF-8/GET hello:world\nGET hello:world\nGET hello:world\n");
			}
		}
	}
	
	@Path("/getfilterbyheader")
//	@Header(key = "Host", pattern = "127\\.0\\.0\\.1\\:*")
	public static final class TestGetWithHostFilterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message, @HeaderParameter("Host") String host) {
			return Http.ok().content("GET hello:" + message + " " + host);
		}
	}
	@Path("/getfilterbyheader2")
//	@Header(key = "Host", pattern = "127\\.0\\.0\\.1\\:*")
	public static final class TestGetWithHostFilterController2 implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message, @HeaderParameter("Host") String host) {
			return Http.ok().content("GET hello:" + message + " " + host);
		}
	}

	@Test
	public void testGetWithHostFilter() throws Exception {
		int new_port = findAvailablePort();
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.routedServer(ninio, new_port, port, new TestUtils.Visitor() {
				@Override
				public void visit(Builder builder) {
					builder.register(null, TestGetWithHostFilterController.class);
					builder.register(null, TestGetWithHostFilterController2.class);
				}
			})) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/getfilterbyheader/hello?message=world")).isEqualTo("text/plain; charset=UTF-8/GET hello:world 127.0.0.1:"+port+"\n");
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+new_port+"/getfilterbyheader2/hello?message=world")).isEqualTo("text/plain; charset=UTF-8/GET hello:world 127.0.0.1:"+new_port+"\n");
			}
		}
	}
	
	@Test
	public void testGetWithHostFilterSame() throws Exception {
		testGetWithHostFilter();
	}
	
	@Path("/get")
	@Assets
	public static final class TestGetWithQueryParameterAssetsController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}

	@Test
	public void testFiles() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, port, new TestUtils.ControllerVisitor(TestGetWithQueryParameterAssetsController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/get/files/index.html")).isEqualTo("text/html; charset=UTF-8/<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><div>Hello</div></body>\n");
			}
		}
	}

	@Test
	public void testFilesDefaultAssets() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, port, new TestUtils.ControllerVisitor(TestGetWithQueryParameterAssetsController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/files/index.html")).isEqualTo("text/html; charset=UTF-8/<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><div>Hello</div></body>\n");
			}
		}
	}

	@Test
	public void testFilesWithPortRouting() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.routedServer(ninio, routerPort, port, new TestUtils.ControllerVisitor(TestGetWithQueryParameterAssetsController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+routerPort+"/get/files/index.html")).isEqualTo("text/html; charset=UTF-8/<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><div>Hello</div></body>\n");
			}
		}
	}
	@Test
	public void testInsideFilesWithPortRouting() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.routedServer(ninio, routerPort, port, new TestUtils.ControllerVisitor(TestGetWithQueryParameterAssetsController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+routerPort+"/get/files/in-dir/in-file.html")).isEqualTo("text/html; charset=UTF-8/<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><div>Hello inside</div></body>\n");
			}
		}
	}

	@Path("/get")
	@Assets(path = "/files")
	public static final class TestGetWithQueryParameterAssetsIndexController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}


	@Test
	public void testRootIndexFilesWithPortRouting() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.routedServer(ninio, routerPort, port, new TestUtils.ControllerVisitor(TestGetWithQueryParameterAssetsIndexController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+routerPort+"/get")).isEqualTo("text/html; charset=UTF-8/<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><div>Hello</div></body>\n");
			}
		}
	}
	@Test
	public void testRootIndexFilesWithPortRoutingDefaultAssets() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.routedServer(ninio, routerPort, port, new TestUtils.ControllerVisitor(TestGetWithQueryParameterAssetsIndexController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+routerPort+"/files")).isEqualTo("text/html; charset=UTF-8/<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><div>Hello</div></body>\n");
			}
		}
	}
	@Test
	public void testInsideRootIndexFilesWithPortRouting() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.routedServer(ninio, routerPort, port, new TestUtils.ControllerVisitor(TestGetWithQueryParameterAssetsIndexController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+routerPort+"/get/in-dir")).isEqualTo("text/html; charset=UTF-8/<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><div>Hello index</div></body>\n");
			}
		}
	}
	
	@Test
	public void testFilesOnly() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, port, new TestUtils.Visitor() {
				@Override
				public void visit(Builder builder) {
				}
			})) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/files/index.html")).isEqualTo("text/html; charset=UTF-8/<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><div>Hello</div></body>\n");
			}
		}
	}
	
	@Path("/get")
	public static final class TestGetWithRootPathController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}
	@Test
	public void testGetWithRootPath() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, port, new TestUtils.Visitor() {
				@Override
				public void visit(Builder builder) {
					builder.register("/root", TestGetWithRootPathController.class);
				}
			})) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:"+port+"/root/get/hello?message=world")).isEqualTo("text/plain; charset=UTF-8/GET hello:world\n");
			}
		}
	}

}
