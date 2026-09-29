package com.lotus.bixi.quartz.util;

import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.exception.TaskException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class RestTaskInvokTest {

	private static final String ALLOWED_HOSTS = "bixi.quartz.rest.allowed-hosts";

	private static final String TIMEOUT_MILLIS = "bixi.quartz.rest.timeout-millis";

	private static final String MAX_RESPONSE_BYTES = "bixi.quartz.rest.max-response-bytes";

	private HttpServer server;

	@AfterEach
	void stopServer() {
		System.clearProperty(ALLOWED_HOSTS);
		System.clearProperty(TIMEOUT_MILLIS);
		System.clearProperty(MAX_RESPONSE_BYTES);
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void acceptsA2xxResponse() throws Exception {
		allowLocalTestHost();
		startServer(204, "");

		assertThatCode(() -> new RestTaskInvok().invokMethod(job()))
			.doesNotThrowAnyException();
	}

	@Test
	void rejectsAnHttp500Response() throws Exception {
		allowLocalTestHost();
		startServer(500, "internal failure");

		assertThatThrownBy(() -> new RestTaskInvok().invokMethod(job()))
			.isInstanceOf(TaskException.class)
				.hasMessageContaining("500");
	}

	@Test
	void rejectsLoopbackUnlessTheHostIsExplicitlyAllowlisted() throws Exception {
		startServer(204, "");

		assertThatThrownBy(() -> new RestTaskInvok().invokMethod(job()))
			.isInstanceOf(TaskException.class)
			.hasMessageContaining("地址");
	}

	@Test
	void appliesAFiniteReadTimeoutToAStalledResponse() throws Exception {
		allowLocalTestHost();
		System.setProperty(TIMEOUT_MILLIS, "100");
		startServer(204, "", 1200);

		long started = System.nanoTime();
		assertThatThrownBy(() -> new RestTaskInvok().invokMethod(job()))
			.isInstanceOf(TaskException.class);
		long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
		assertThat(elapsedMillis).isLessThan(900);
	}

	@Test
	void doesNotFollowRedirectResponses() throws Exception {
		allowLocalTestHost();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/redirect", exchange -> {
			exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/job");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});
		server.createContext("/job", exchange -> {
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
		});
		server.start();

		assertThatThrownBy(() -> new RestTaskInvok().invokMethod(jobAt("/redirect")))
			.isInstanceOf(TaskException.class)
			.hasMessageContaining("302");
	}

	@Test
	void rejectsAResponseBodyOverTheConfiguredLimit() throws Exception {
		allowLocalTestHost();
		System.setProperty(MAX_RESPONSE_BYTES, "8");
		startServer(200, "123456789");

		assertThatThrownBy(() -> new RestTaskInvok().invokMethod(job()))
			.isInstanceOf(TaskException.class)
			.hasMessageContaining("响应体");
	}

	@Test
	void rejectsAChunkedResponseBodyOverTheConfiguredLimit() throws Exception {
		allowLocalTestHost();
		System.setProperty(MAX_RESPONSE_BYTES, "8");
		startChunkedServer(200, "123456789");

		assertThatThrownBy(() -> new RestTaskInvok().invokMethod(job()))
			.isInstanceOf(TaskException.class)
			.hasMessageContaining("响应体");
	}

	@Test
	void connectsUsingTheAddressValidatedByTheUrlPolicy() throws Exception {
		startServer(204, "");
		RestTaskUrlPolicy policy = new RestTaskUrlPolicy("dns-pin-test.invalid", host -> {
			assertThat(host).isEqualTo("dns-pin-test.invalid");
			return new InetAddress[] { InetAddress.getByName("127.0.0.1") };
		});
		RestTaskInvok invoker = new RestTaskInvok(policy, 1000, 1024);

		assertThatCode(() -> invoker.invokMethod(jobAtUrl(
				"http://dns-pin-test.invalid:" + server.getAddress().getPort() + "/job")))
			.doesNotThrowAnyException();
		assertThatCode(() -> invoker.invokMethod(jobAtUrl(
				"http://dns-pin-test.invalid.:" + server.getAddress().getPort() + "/job")))
			.doesNotThrowAnyException();
	}

	private void allowLocalTestHost() {
		System.setProperty(ALLOWED_HOSTS, "127.0.0.1");
	}

	private void startServer(int status, String body) throws IOException {
		startServer(status, body, 0);
	}

	private void startServer(int status, String body, long delayMillis) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/job", exchange -> {
			if (delayMillis > 0) {
				try {
					Thread.sleep(delayMillis);
				}
				catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
	}

	private void startChunkedServer(int status, String body) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/job", exchange -> {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, 0);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
	}

	private SysJob job() {
		return jobAt("/job");
	}

	private SysJob jobAt(String path) {
		return jobAtUrl("http://127.0.0.1:" + server.getAddress().getPort() + path);
	}

	private SysJob jobAtUrl(String url) {
		return SysJob.builder()
			.name("rest-job")
			.executePath(url)
			.build();
	}
}
