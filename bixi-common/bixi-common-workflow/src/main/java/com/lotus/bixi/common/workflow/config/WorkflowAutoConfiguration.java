package com.lotus.bixi.common.workflow.config;

import java.util.concurrent.Executor;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.flowable.spring.boot.ProcessEngineAutoConfiguration;
import org.flowable.spring.boot.process.Process;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

@AutoConfiguration(before = ProcessEngineAutoConfiguration.class)
@EnableConfigurationProperties(WorkflowProperties.class)
@ConditionalOnProperty(prefix = "workflow", name = "enabled", havingValue = "true")
public class WorkflowAutoConfiguration {

    private final WorkflowProperties workflowProperties;

    public WorkflowAutoConfiguration(WorkflowProperties workflowProperties) {
        this.workflowProperties = workflowProperties;
    }

    /** Flowable expects Spring's executor contract; DynamicTp owns the underlying pool and its lifecycle. */
    @Bean
    @Process
    @ConditionalOnBean(name = "taskExecutor")
    @ConditionalOnMissingBean(AsyncTaskExecutor.class)
    public AsyncTaskExecutor workflowTaskExecutor(@Qualifier("taskExecutor") Executor executor) {
        return new TaskExecutorAdapter(executor);
    }

    @Bean
    @ConditionalOnMissingBean(name = "processEngineConfigurationConfigurer")
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> processEngineConfigurationConfigurer() {
        return configuration -> {
            configuration.setAsyncExecutorActivate(workflowProperties.getAsyncExecutorActivate());
            configuration.setDatabaseSchemaUpdate(workflowProperties.getDatabaseSchemaUpdate());
            configuration.setHistoryLevel(HistoryLevel.getHistoryLevelForKey(workflowProperties.getHistoryLevel()));
            // Bixi owns users and groups; this integration only runs the BPMN engine.
            configuration.setDisableIdmEngine(true);
            configuration.setDisableEventRegistry(true);
        };
    }

}
