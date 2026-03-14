package com.lotus.bixi.common.ai.config;

import com.lotus.bixi.common.ai.properties.AiProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * AI 自动配置类
 *
 * @author bixi
 * @date 2025-01-01
 */
@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "spring.ai.alibaba.dashscope", name = "api-key")
public class AiAutoConfiguration {

    /**
     * Create ChatClient bean.
     * Only created if 'spring.ai.alibaba.dashscope.chat.enabled' is true (default is true).
     *
     * @param builder ChatClient.Builder provided by Spring AI
     * @return ChatClient instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.dashscope.chat", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
