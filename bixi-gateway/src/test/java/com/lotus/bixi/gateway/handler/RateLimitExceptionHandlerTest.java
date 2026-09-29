package com.lotus.bixi.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitExceptionHandlerTest {

	private final RateLimitExceptionHandler handler = new RateLimitExceptionHandler(new ObjectMapper());

	@Test
	void propagatesUnexpectedFailuresWithoutChangingTheResponse() {
		MockServerWebExchange exchange = exchange();
		IllegalStateException failure = new IllegalStateException("backend unavailable");

		assertThatThrownBy(() -> handler.handle(exchange, failure).block())
				.isSameAs(failure);
		assertThat(exchange.getResponse().getStatusCode()).isNull();
	}

	@Test
	void propagatesNonRateLimitResponseStatusFailures() {
		MockServerWebExchange exchange = exchange();
		ResponseStatusException failure = new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);

		assertThatThrownBy(() -> handler.handle(exchange, failure).block())
				.isSameAs(failure);
		assertThat(exchange.getResponse().getStatusCode()).isNull();
	}

	@Test
	void rendersOnlyExplicitRateLimitFailures() {
		MockServerWebExchange exchange = exchange();

		handler.handle(exchange, new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)).block();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(exchange.getResponse().getBodyAsString().block()).contains("请求过于频繁");
	}

	private static MockServerWebExchange exchange() {
		return MockServerWebExchange.from(MockServerHttpRequest.get("/admin/workflow/process/list").build());
	}
}
