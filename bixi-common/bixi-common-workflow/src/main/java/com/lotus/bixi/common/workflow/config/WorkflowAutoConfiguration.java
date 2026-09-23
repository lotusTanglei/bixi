package com.lotus.bixi.common.workflow.config;

import java.util.concurrent.Executor;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.job.service.impl.asyncexecutor.AsyncJobExecutorConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.flowable.spring.boot.ProcessEngineAutoConfiguration;
import org.flowable.spring.boot.process.Process;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.core.annotation.Order;

import java.time.Duration;

@AutoConfiguration(before = ProcessEngineAutoConfiguration.class)
@EnableConfigurationProperties(WorkflowProperties.class)
@ConditionalOnProperty(prefix = "workflow", name = "enabled", havingValue = "true")
public class WorkflowAutoConfiguration {

    private static final String PROCESS_ASYNC_EXECUTOR_CONFIGURATION_BEAN = "processAsyncExecutorConfiguration";

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
    @Order(Ordered.LOWEST_PRECEDENCE)
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration>
            bixiWorkflowProcessEngineConfigurationConfigurer() {
        return configuration -> {
            configuration.setAsyncExecutorActivate(workflowProperties.getAsyncExecutorActivate());
            configuration.setDatabaseSchemaUpdate(workflowProperties.getDatabaseSchemaUpdate());
            configuration.setHistoryLevel(HistoryLevel.getHistoryLevelForKey(workflowProperties.getHistoryLevel()));
            configuration.setAsyncExecutorLockOwner(requireText(workflowProperties.getLockOwner(), "lockOwner"));
            configuration.setAsyncExecutorAsyncJobLockTimeInMillis(
                    millis(workflowProperties.getAsyncJobLockTime(), "asyncJobLockTime"));
            configuration.setAsyncExecutorTimerLockTimeInMillis(
                    millis(workflowProperties.getTimerLockTime(), "timerLockTime"));
            configuration.setAsyncExecutorResetExpiredJobsInterval(
                    millis(workflowProperties.getResetExpiredJobsInterval(), "resetExpiredJobsInterval"));
            configuration.setAsyncExecutorDefaultAsyncJobAcquireWaitTime(
                    millis(workflowProperties.getDefaultAsyncJobAcquireWaitTime(), "defaultAsyncJobAcquireWaitTime"));
            configuration.setAsyncExecutorDefaultTimerJobAcquireWaitTime(
                    millis(workflowProperties.getDefaultTimerJobAcquireWaitTime(), "defaultTimerJobAcquireWaitTime"));
            configuration.setAsyncExecutorMaxAsyncJobsDuePerAcquisition(
                    positive(workflowProperties.getMaxAsyncJobsDuePerAcquisition(), "maxAsyncJobsDuePerAcquisition"));
            configuration.setAsyncExecutorMaxTimerJobsPerAcquisition(
                    positive(workflowProperties.getMaxTimerJobsPerAcquisition(), "maxTimerJobsPerAcquisition"));
            configuration.setAsyncExecutorResetExpiredJobsPageSize(
                    positive(workflowProperties.getResetExpiredJobsPageSize(), "resetExpiredJobsPageSize"));
            configuration.setAsyncExecutorResetExpiredJobsEnabled(
                    Boolean.TRUE.equals(workflowProperties.getResetExpiredJobEnabled()));
            configuration.setAsyncExecutorUnlockOwnedJobs(
                    Boolean.TRUE.equals(workflowProperties.getUnlockOwnedJobs()));
            // Bixi owns users and groups; this integration only runs the BPMN engine.
            configuration.setDisableIdmEngine(true);
            configuration.setDisableEventRegistry(true);
        };
    }

    /**
     * Flowable 7.1.0 only invokes generic process configurers through optional
     * engine configurators. The standalone process starter does not create
     * those engines, so apply Bixi's settings directly before the engine builds.
     */
    @Bean
    public static BeanPostProcessor bixiWorkflowProcessEngineConfigurationPostProcessor(
            @Qualifier("bixiWorkflowProcessEngineConfigurationConfigurer")
            EngineConfigurationConfigurer<SpringProcessEngineConfiguration> configurer,
            WorkflowProperties workflowProperties) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof SpringProcessEngineConfiguration configuration) {
                    configurer.configure(configuration);
                }
                if (PROCESS_ASYNC_EXECUTOR_CONFIGURATION_BEAN.equals(beanName)
                        && bean instanceof AsyncJobExecutorConfiguration configuration) {
                    configureProcessAsyncExecutor(configuration, workflowProperties);
                }
                return bean;
            }
        };
    }

    private static void configureProcessAsyncExecutor(AsyncJobExecutorConfiguration configuration,
            WorkflowProperties properties) {
        configuration.setLockOwner(requireText(properties.getLockOwner(), "lockOwner"));
        configuration.setAsyncJobLockTimeInMillis(millis(properties.getAsyncJobLockTime(), "asyncJobLockTime"));
        configuration.setTimerLockTimeInMillis(millis(properties.getTimerLockTime(), "timerLockTime"));
        configuration.setResetExpiredJobsInterval(
                properties.getResetExpiredJobsInterval());
        configuration.setDefaultAsyncJobAcquireWaitTimeInMillis(
                millis(properties.getDefaultAsyncJobAcquireWaitTime(), "defaultAsyncJobAcquireWaitTime"));
        configuration.setDefaultTimerJobAcquireWaitTimeInMillis(
                millis(properties.getDefaultTimerJobAcquireWaitTime(), "defaultTimerJobAcquireWaitTime"));
        configuration.setMaxAsyncJobsDuePerAcquisition(
                positive(properties.getMaxAsyncJobsDuePerAcquisition(), "maxAsyncJobsDuePerAcquisition"));
        configuration.setMaxTimerJobsPerAcquisition(
                positive(properties.getMaxTimerJobsPerAcquisition(), "maxTimerJobsPerAcquisition"));
        configuration.setResetExpiredJobsPageSize(
                positive(properties.getResetExpiredJobsPageSize(), "resetExpiredJobsPageSize"));
        configuration.setResetExpiredJobEnabled(Boolean.TRUE.equals(properties.getResetExpiredJobEnabled()));
        configuration.setUnlockOwnedJobs(Boolean.TRUE.equals(properties.getUnlockOwnedJobs()));
    }

    private static int millis(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("workflow." + name + " must be positive");
        }
        try {
            return Math.toIntExact(value.toMillis());
        }
        catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("workflow." + name + " is too large", overflow);
        }
    }

    private static int positive(Integer value, String name) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException("workflow." + name + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("workflow." + name + " must be a non-empty value of at most 128 characters");
        }
        return value;
    }

}
