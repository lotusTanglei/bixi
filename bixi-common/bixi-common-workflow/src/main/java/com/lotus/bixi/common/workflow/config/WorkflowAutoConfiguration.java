package com.lotus.bixi.common.workflow.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(WorkflowProperties.class)
@ConditionalOnProperty(prefix = "workflow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowAutoConfiguration {

    private final WorkflowProperties workflowProperties;

    public WorkflowAutoConfiguration(WorkflowProperties workflowProperties) {
        this.workflowProperties = workflowProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> processEngineConfigurationConfigurer() {
        return configuration -> {
            configuration.setAsyncExecutorActivate(workflowProperties.getAsyncExecutorActivate());
            configuration.setDatabaseSchemaUpdate(workflowProperties.getDatabaseSchemaUpdate());
        };
    }

}
