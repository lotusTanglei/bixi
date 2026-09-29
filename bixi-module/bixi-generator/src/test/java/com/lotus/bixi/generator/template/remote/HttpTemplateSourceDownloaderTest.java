package com.lotus.bixi.generator.template.remote;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpTemplateSourceDownloaderTest {

	private HttpServer server;
	private URI root;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.start();
		root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void acceptsAResponseAtTheExactSizeLimit() {
		byte[] body = "exact".getBytes(StandardCharsets.UTF_8);
		server.createContext("/exact", exchange -> respond(exchange, 200, body));

		assertThat(new HttpTemplateSourceDownloader().download(root.resolve("exact"), body.length))
				.isEqualTo(body);
	}

	@Test
	void rejectsBodiesLargerThanTheLimitEvenWithoutADeclaredLength() {
		byte[] body = "oversized".getBytes(StandardCharsets.UTF_8);
		server.createContext("/oversized", exchange -> {
			exchange.sendResponseHeaders(200, 0);
			exchange.getResponseBody().write(body);
			exchange.close();
		});

		assertThatThrownBy(() -> new HttpTemplateSourceDownloader().download(root.resolve("oversized"), body.length - 1))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("大小限制");
	}

	@Test
	void rejectsRedirectsAndNonSuccessResponses() {
		server.createContext("/redirect", exchange -> {
			exchange.getResponseHeaders().add("Location", root.resolve("exact").toString());
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});
		server.createContext("/missing", exchange -> respond(exchange, 404, new byte[0]));

		HttpTemplateSourceDownloader downloader = new HttpTemplateSourceDownloader();
		assertThatThrownBy(() -> downloader.download(root.resolve("redirect"), 16))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("302");
		assertThatThrownBy(() -> downloader.download(root.resolve("missing"), 16))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("404");
	}

	private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
		exchange.sendResponseHeaders(status, body.length);
		if (body.length > 0) exchange.getResponseBody().write(body);
		exchange.close();
	}
}
