package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.sensitive.SensitiveWordEngine;
import com.lotus.bixi.upms.api.entity.SysSensitiveWord;
import com.lotus.bixi.upms.mapper.SysSensitiveWordMapper;
import com.lotus.bixi.upms.service.SensitiveWordRefreshNotifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysSensitiveWordServiceImplTransactionTest {

    @Mock
    SysSensitiveWordMapper mapper;

    @Mock
    SensitiveWordEngine engine;

    @Mock
    SensitiveWordRefreshNotifier notifier;

    @InjectMocks
    SysSensitiveWordServiceImpl service;

    @AfterEach
    void clearContext() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TenantContextHolder.clear();
    }

    @Test
    void saveDefersRefreshAndPublishUntilTransactionCommit() {
        TenantContextHolder.set(7L);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(mapper.insert(any(SysSensitiveWord.class))).thenReturn(1);

        assertThat(service.save(new SysSensitiveWord())).isTrue();
        verifyNoInteractions(engine, notifier);

        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

        verify(engine).reload(eq(7L), anyList());
        verify(notifier).publish(7L);
    }
}
