package com.lotus.bixi.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 自定义限流过滤器（示例）
 * 注意：实际使用时，建议通过配置文件使用 Spring Cloud Gateway 自带的
 * RequestRateLimiter GatewayFilter Factory，更加简单高效
 *
 * @author bixi
 * @date 2026-03-28
 */
@Slf4j
@Component
public class CustomRateLimitFilter implements GatewayFilter, Ordered {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		// 这里仅作为示例，实际限流建议使用配置文件方式
		// 请参考 application.yml 中的配置示例
		return chain.filter(exchange);
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 10;
	}

}
