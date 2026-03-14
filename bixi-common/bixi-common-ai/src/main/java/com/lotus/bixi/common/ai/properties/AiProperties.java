package com.lotus.bixi.common.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 配置属性
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@ConfigurationProperties(prefix = "spring.ai.alibaba.dashscope")
public class AiProperties {

    private String apiKey;

    private ChatOptions chat = new ChatOptions();

    private EmbeddingOptions embedding = new EmbeddingOptions();

    @Data
    public static class ChatOptions {

        private String model = "qwen-plus";

        private Double temperature = 0.7;

        private Double topP = 0.9;

        private Integer maxTokens = 2000;

        private Boolean enabled = true;
    }

    @Data
    public static class EmbeddingOptions {

        private String model = "text-embedding-v2";

        private Boolean enabled = true;
    }
}
