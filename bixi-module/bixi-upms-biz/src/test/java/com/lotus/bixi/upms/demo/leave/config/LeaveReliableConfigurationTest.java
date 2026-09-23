package com.lotus.bixi.upms.demo.leave.config;

import com.lotus.bixi.common.mq.reliable.InboxExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveReliableConfigurationTest {
    @Test
    void singleModeTransportUsesTheWorkflowInboxExecutorParameter() throws Exception {
        Method method = LeaveReliableConfiguration.class.getDeclaredMethod(
                "upmsLocalTransport", InboxExecutor.class);

        Qualifier qualifier = method.getParameters()[0].getAnnotation(Qualifier.class);

        assertThat(qualifier).isNotNull();
        assertThat(qualifier.value()).isEqualTo("workflowInboxExecutor");
    }
}
