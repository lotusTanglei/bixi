package com.lotus.bixi.workflow.config;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.listener.WorkflowCompletionListener;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWorkflowEnabled
public class WorkflowEngineEventsConfiguration {
    @Bean
    EngineConfigurationConfigurer<SpringProcessEngineConfiguration> workflowCompletionEvents(WorkflowCompletionListener listener) {
        return configuration -> {
            List<FlowableEventListener> listeners = new ArrayList<>();
            if (configuration.getEventListeners() != null) {
                listeners.addAll(configuration.getEventListeners());
            }
            listeners.add(listener);
            configuration.setEventListeners(listeners);
        };
    }
}
