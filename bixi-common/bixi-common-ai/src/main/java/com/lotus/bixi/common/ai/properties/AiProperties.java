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

    /**
     * DashScope API Key
     */
    private String apiKey;

    /**
     * Chat configuration
     */
    private Chat chat = new Chat();

    /**
     * Embedding configuration
     */
    private Embedding embedding = new Embedding();

    /**
     * Chat properties
     */
    @Data
    public static class Chat {
        /**
         * Chat options
         */
        private ChatOptions options = new ChatOptions();
        
        /**
         * Whether to enable chat support
         */
        private Boolean enabled = true;
    }

    /**
     * Embedding properties
     */
    @Data
    public static class Embedding {
        /**
         * Embedding options
         */
        private EmbeddingOptions options = new EmbeddingOptions();
        
        /**
         * Whether to enable embedding support
         */
        private Boolean enabled = true;
    }

    /**
     * Chat options
     */
    @Data
    public static class ChatOptions {

        /**
         * Model name, e.g., qwen-plus
         */
        private String model = "qwen-plus";

        /**
         * Sampling temperature
         */
        private Double temperature = 0.7;

        /**
         * Nucleus sampling probability
         */
        private Double topP = 0.9;

        /**
         * Max tokens to generate
         */
        private Integer maxTokens = 2000;
    }

    /**
     * Embedding options
     */
    @Data
    public static class EmbeddingOptions {

        /**
         * Model name, e.g., text-embedding-v2
         */
        private String model = "text-embedding-v2";
    }
}
