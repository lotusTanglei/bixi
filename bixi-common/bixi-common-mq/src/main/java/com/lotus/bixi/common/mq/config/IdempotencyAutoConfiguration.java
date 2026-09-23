package com.lotus.bixi.common.mq.config;

import com.lotus.bixi.common.mq.reliable.JdbcIdempotencyStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

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
}
