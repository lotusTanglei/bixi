package com.lotus.bixi.ai.api.constant;

/**
 * AI 服务常量
 *
 * @author bixi
 * @date 2025-01-01
 */
public interface AiConstants {

    String AI_SERVICE = "bixi-ai";

    String DEFAULT_MODEL = "qwen-plus";

    String DEFAULT_EMBEDDING_MODEL = "text-embedding-v2";

    Integer MAX_INPUT_LENGTH = 10000;

    Integer DEFAULT_TOP_K = 5;
}
