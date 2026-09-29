package com.lotus.bixi.common.mq.reliable;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Shared business entry point for request idempotency. The executor owns the
 * request hash and replay protocol while the callback owns the business side effect.
 */
public final class IdempotencyExecutor {

    private final JdbcIdempotencyStore store;
    private final ObjectMapper mapper;
    private final Duration timeout;

    public IdempotencyExecutor(JdbcIdempotencyStore store, ObjectMapper mapper, Duration timeout) {
        this.store = Objects.requireNonNull(store, "store is required");
        this.mapper = Objects.requireNonNull(mapper, "mapper is required");
        this.timeout = Objects.requireNonNull(timeout, "timeout is required");
    }

    public <T> T execute(Long tenantId, String scope, String key, Object request,
                         Class<T> resultType, Work<T> work) throws Exception {
        Objects.requireNonNull(resultType, "resultType is required");
        return execute(tenantId, scope, key, request, mapper.getTypeFactory().constructType(resultType), work);
    }

    public <T> T execute(Long tenantId, String scope, String key, Object request,
                         com.fasterxml.jackson.databind.JavaType resultType, Work<T> work) throws Exception {
        Objects.requireNonNull(resultType, "resultType is required");
        Objects.requireNonNull(work, "work is required");
        String requestHash = hash(request);
        JdbcIdempotencyStore.Decision decision = store.begin(tenantId, scope, key, requestHash, timeout);
        if (decision.state() == JdbcIdempotencyStore.State.REPLAY) {
            return mapper.readValue(decision.responseBody(), resultType);
        }
        if (decision.state() == JdbcIdempotencyStore.State.CONFLICT) {
            throw new IllegalArgumentException("idempotency_request_conflict");
        }
        if (decision.state() == JdbcIdempotencyStore.State.IN_PROGRESS) {
            throw new IllegalArgumentException("idempotency_in_progress");
        }
        if (decision.state() == JdbcIdempotencyStore.State.FAILED) {
            throw new IllegalArgumentException("idempotency_failed");
        }
        if (decision.state() != JdbcIdempotencyStore.State.ACQUIRED) {
            throw new IllegalStateException("idempotency_unexpected_state");
        }

        try {
            T result = work.run();
            store.complete(tenantId, scope, key, requestHash, responseCode(result), mapper.writeValueAsString(result));
            return result;
        }
        catch (Throwable failure) {
            try {
                store.fail(tenantId, scope, key, requestHash, 1, failure);
            }
            catch (RuntimeException stateFailure) {
                failure.addSuppressed(stateFailure);
            }
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException(failure);
        }
    }

    private String hash(Object request) throws Exception {
        byte[] payload = mapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .writeValueAsBytes(request);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private int responseCode(Object result) {
        if (result instanceof com.lotus.bixi.common.core.util.R<?> response) {
            return response.getCode();
        }
        return 0;
    }

    @FunctionalInterface
    public interface Work<T> {
        T run() throws Exception;
    }
}
