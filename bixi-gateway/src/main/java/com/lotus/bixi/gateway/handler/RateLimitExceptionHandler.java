package com.lotus.bixi.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.util.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 限流异常处理器
 * 当请求超过速率限制时，返回友好的错误信息
 *
 * @author bixi
 * @date 2026-03-28
 */
@Slf4j
@Order(-1)
@Component
@RequiredArgsConstructor
public class RateLimitExceptionHandler implements ErrorWebExceptionHandler {

	private final ObjectMapper objectMapper;

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
		ServerHttpResponse response = exchange.getResponse();

		if (ex instanceof ResponseStatusException rse) {
			if (rse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
				return handleRateLimitExceeded(response, exchange);
			}
		}

		// 其他异常类型处理
		if (response.isCommitted()) {
			return Mono.error(ex);
		}

		response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
		return handleRateLimitExceeded(response, exchange);
	}

	/**
	 * 处理限流异常
	 */
	private Mono<Void> handleRateLimitExceeded(ServerHttpResponse response, ServerWebExchange exchange) {
		response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

		// 记录限流日志
		String clientIp = getClientIp(exchange);
		String requestPath = exchange.getRequest().getPath().value();
		log.warn("API 速率限制触发 - IP: {}, 路径: {}, 时间: {}", clientIp, requestPath,
				System.currentTimeMillis());

		// 构造返回结果
		R<Void> result = R.failed("请求过于频繁，请稍后再试");

		try {
			byte[] bytes = objectMapper.writeValueAsBytes(result);
			DataBuffer buffer = response.bufferFactory().wrap(bytes);
			return response.writeWith(Mono.just(buffer));
		}
		catch (JsonProcessingException e) {
			log.error("序列化限流响应失败", e);
			return Mono.error(e);
		}
	}

	/**
	 * 获取客户端真实 IP
	 */
	private String getClientIp(ServerWebExchange exchange) {
		String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
		if (ip == null || ip.isEmpty()) {
			ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
		}
		if (ip == null || ip.isEmpty()) {
			ip = exchange.getRequest().getRemoteAddress() != null
					? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
					: "unknown";
		}
		return ip;
	}

}
