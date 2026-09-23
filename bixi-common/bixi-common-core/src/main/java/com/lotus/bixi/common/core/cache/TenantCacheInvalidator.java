package com.lotus.bixi.common.core.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Removes only Redis entries belonging to one tenant without using KEYS. */
@Component
@RequiredArgsConstructor
public class TenantCacheInvalidator {

    private static final int DELETE_BATCH_SIZE = 128;

    private final RedisTemplate<String, Object> redisTemplate;

    public static String scanPattern(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return "*TENANT:" + tenantId + ":*";
    }

    public long clearTenant(Long tenantId) {
        if (tenantId == null || redisTemplate.getConnectionFactory() == null) {
            return 0L;
        }
        Long deleted = redisTemplate.execute((RedisCallback<Long>) connection -> deleteMatching(connection, scanPattern(tenantId)));
        return deleted == null ? 0L : deleted;
    }

    private long deleteMatching(RedisConnection connection, String pattern) {
        long deleted = 0L;
        List<byte[]> batch = new ArrayList<>(DELETE_BATCH_SIZE);
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
        try (Cursor<byte[]> cursor = connection.scan(options)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() == DELETE_BATCH_SIZE) {
                    deleted += deleteBatch(connection, batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            deleted += deleteBatch(connection, batch);
        }
        return deleted;
    }

    private long deleteBatch(RedisConnection connection, List<byte[]> batch) {
        return connection.del(batch.toArray(byte[][]::new));
    }
}
