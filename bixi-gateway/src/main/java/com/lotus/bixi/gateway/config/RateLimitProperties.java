package com.lotus.bixi.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 限流配置属性
 * 支持从配置文件中读取不同接口的限流策略
 *
 * @author bixi
 * @date 2026-03-28
 */
@Data
@Component
@ConfigurationProperties(prefix = "bixi.gateway.rate-limit")
public class RateLimitProperties {

	/**
	 * 是否启用限流
	 */
	private boolean enabled = true;

	/**
	 * 默认限流策略
	 */
	private DefaultStrategy defaultStrategy = new DefaultStrategy();

	/**
	 * 针对特定 API 路径的限流策略
	 * key: 路径前缀（如：/api/ai, /api/user）
	 * value: 该路径的限流配置
	 */
	private Map<String, ApiStrategy> apiStrategies = new HashMap<>();

	/**
	 * 默认限流策略
	 */
	@Data
	public static class DefaultStrategy {

		/**
		 * 令牌桶容量（ replenishRate 和 burstCapacity 配合实现令牌桶算法）
		 * 即：允许在 1 秒内完成的最大请求数
		 */
		private int replenishRate = 100;

		/**
		 * 令牌桶容量
		 * 即：令牌桶的最大容量，用于应对突发流量
		 */
		private int burstCapacity = 200;

		/**
		 * 每次请求消耗的令牌数量
		 */
		private int requestedTokens = 1;

	}

	/**
	 * API 特定限流策略
	 */
	@Data
	public static class ApiStrategy {

		/**
		 * API 路径前缀
		 */
		private String path;

		/**
		 * 令牌补充速率
		 */
		private int replenishRate;

		/**
		 * 令牌桶容量
		 */
		private int burstCapacity;

		/**
		 * 每次请求消耗的令牌数
		 */
		private int requestedTokens = 1;

		/**
		 * 使用的限流 Key 解析器 Bean 名称
		 * 可选值：remoteAddrKeyResolver, userKeyResolver, apiKeyResolver,
		 * compositeKeyResolver, tenantKeyResolver
		 */
		private String keyResolver = "remoteAddrKeyResolver";

	}

}
