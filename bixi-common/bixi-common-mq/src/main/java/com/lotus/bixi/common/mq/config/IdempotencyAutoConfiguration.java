package com.lotus.bixi.common.mq.config;

import com.lotus.bixi.common.mq.reliable.JdbcIdempotencyStore;
import com.lotus.bixi.common.mq.reliable.IdempotencyAspect;
import com.lotus.bixi.common.mq.reliable.IdempotencyExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.time.Duration;

/** Exposes the shared idempotency store only for a local JDBC transaction boundary. */
@AutoConfiguration
@ConditionalOnSingleCandidate(DataSource.class)
@ConditionalOnBean(DataSourceTransactionManager.class)
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JdbcIdempotencyStore jdbcIdempotencyStore(DataSource dataSource,
                                                     DataSourceTransactionManager transactionManager) {
        return new JdbcIdempotencyStore(dataSource, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyExecutor idempotencyExecutor(JdbcIdempotencyStore store, ObjectMapper objectMapper) {
        return new IdempotencyExecutor(store, objectMapper, Duration.ofMinutes(5));
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyAspect idempotencyAspect(IdempotencyExecutor executor, ObjectMapper objectMapper) {
        return new IdempotencyAspect(executor, objectMapper);
    }
}
