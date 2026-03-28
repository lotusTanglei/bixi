package com.lotus.bixi.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * API 速率限制配置
 * 提供多种维度的限流策略
 *
 * @author bixi
 * @date 2026-03-28
 */
@Configuration(proxyBeanMethods = false)
public class RateLimiterConfiguration {

	/**
	 * 基于远程 IP 地址的限流 Key 解析器
	 * 适用场景：防止 DDoS 攻击、限制单 IP 请求频率
	 */
	@Bean
	public KeyResolver remoteAddrKeyResolver() {
		return exchange -> {
			String remoteAddr = exchange.getRequest().getRemoteAddress() != null
					? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
					: "0.0.0.0";
			return Mono.just(remoteAddr);
		};
	}

	/**
	 * 基于用户 ID 的限流 Key 解析器
	 * 适用场景：限制单个用户的请求频率（需要认证）
	 */
	@Bean
	public KeyResolver userKeyResolver() {
		return exchange -> {
			// 从请求头中获取用户信息（通过认证网关传递）
			String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
			if (userId == null) {
				// 如果没有用户ID，降级为 IP 限流
				String remoteAddr = exchange.getRequest().getRemoteAddress() != null
						? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
						: "0.0.0.0";
				return Mono.just("user:anon:" + remoteAddr);
			}
			return Mono.just("user:" + userId);
		};
	}

	/**
	 * 基于 API 路径的限流 Key 解析器
	 * 适用场景：针对特定接口进行限流（如：AI 对话接口）
	 */
	@Bean
	public KeyResolver apiKeyResolver() {
		return exchange -> {
			String path = exchange.getRequest().getPath().value();
			// 提取路径的第一级作为 API 分组
			String[] parts = path.split("/");
			String apiGroup = parts.length > 1 ? parts[1] : "default";
			return Mono.just("api:" + apiGroup);
		};
	}

	/**
	 * 组合式限流 Key 解析器
	 * 适用场景：同时考虑用户 ID 和 API 路径，实现精细化限流
	 * 例如：user:123:api:ai 表示用户 123 访问 AI 接口
	 */
	@Bean
	public KeyResolver compositeKeyResolver() {
		return exchange -> {
			// 获取用户 ID
			String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
			if (userId == null) {
				// 未认证用户，使用 IP
				userId = exchange.getRequest().getRemoteAddress() != null
						? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
						: "0.0.0.0";
			}

			// 获取 API 路径
			String path = exchange.getRequest().getPath().value();
			String[] parts = path.split("/");
			String apiGroup = parts.length > 1 ? parts[1] : "default";

			return Mono.just(String.format("composite:%s:api:%s", userId, apiGroup));
		};
	}

	/**
	 * 基于租户的限流 Key 解析器
	 * 适用场景：多租户系统，限制单个租户的请求频率
	 */
	@Bean
	public KeyResolver tenantKeyResolver() {
		return exchange -> {
			// 从请求头或 JWT Token 中获取租户 ID
			String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
			if (tenantId == null) {
				tenantId = "default";
			}
			return Mono.just("tenant:" + tenantId);
		};
	}

}
