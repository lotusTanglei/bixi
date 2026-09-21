package com.lotus.bixi.common.workflow.config;

import java.util.UUID;
import javax.sql.DataSource;

import org.flowable.common.engine.api.Engine;
import org.flowable.common.engine.impl.AbstractEngineConfiguration;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.ProcessEngine;
import org.flowable.job.service.impl.asyncexecutor.AsyncExecutor;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Real starter assembly against H2; this does not establish production MySQL compatibility. */
class WorkflowEngineConfigurationTest {

    @Test
    void webRuntimeCanUseTheNativeDynamicExecutor() {
        try {
            nativeExecutorRunner(true).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(ProcessEngine.class);
                var nativeExecutor = context.getBean("taskExecutor",
                        org.dromara.dynamictp.core.executor.DtpExecutor.class);
                var engineExecutor = context.getBean(SpringProcessEngineConfiguration.class).getAsyncTaskExecutor();
                assertThat(engineExecutor.submit(() -> Thread.currentThread().getName())
                        .get(5, java.util.concurrent.TimeUnit.SECONDS)).startsWith("Bixi-Async-");
                engineExecutor.shutdown();
                assertThat(nativeExecutor.isShutdown()).isFalse();
            });
        } finally {
            unregisterNativeExecutor();
        }
    }

    @Test
    void disabledWebRuntimeKeepsOnlyTheNativeExecutor() {
        try {
            nativeExecutorRunner(false).run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(Engine.class)
                        .doesNotHaveBean(AsyncExecutor.class).doesNotHaveBean("workflowTaskExecutor")
                        .doesNotHaveBean("flowableAsyncTaskInvokerTaskExecutor");
                assertThat(context.getBean("taskExecutor"))
                        .isInstanceOf(org.dromara.dynamictp.core.executor.DtpExecutor.class);
            });
        } finally {
            unregisterNativeExecutor();
        }
    }

    private WebApplicationContextRunner nativeExecutorRunner(boolean enabled) {
        return new WebApplicationContextRunner().withUserConfiguration(NativeExecutorApplication.class)
                .withInitializer(context -> new org.dromara.dynamictp.starter.common.initializer.DtpApplicationContextInitializer()
                        .initialize(context))
                .withPropertyValues("workflow.enabled=" + enabled, "workflow.database-schema-update=true",
                        "workflow.async-executor-activate=false", "spring.dynamic.tp.enabled=true",
                        "spring.application.name=workflow-native-executor-test",
                        "spring.datasource.url=jdbc:h2:mem:native-executor-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa", "spring.datasource.password=",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.config.enabled=false",
                        "spring.cloud.compatibility-verifier.enabled=false", "spring.boot.admin.client.enabled=false",
                        "flowable.check-process-definitions=false");
    }

    private void unregisterNativeExecutor() {
        if (org.dromara.dynamictp.core.DtpRegistry.getAllExecutorNames().contains("taskExecutor")) {
            org.dromara.dynamictp.core.DtpRegistry.unregisterExecutor("taskExecutor");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = "com.lotus.bixi.common.core.config.WebMvcConfiguration")
    static class NativeExecutorApplication {
    }

    private static final String PROCESS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         targetNamespace="https://bixi.example/workflow/test">
              <process id="assemblyApproval" name="Assembly approval" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="startReview" sourceRef="start" targetRef="review"/>
                <userTask id="review" name="Review"/>
                <sequenceFlow id="reviewEnd" sourceRef="review" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;

    @Test
    void workflowIsDisabledByDefaultEvenWhenFlowableFlagsAreEnabled() {
        runner().run(this::assertNoWorkflowEngine);
    }

    @Test
    void explicitDisablePreventsEveryFlowableEngineAndExecutor() {
        runner().withPropertyValues("workflow.enabled=false").run(this::assertNoWorkflowEngine);
    }

    @Test
    void onlyExplicitTrueCanEnableWorkflow() {
        runner().withPropertyValues("workflow.enabled=yes").run(this::assertNoWorkflowEngine);
    }

    @Test
    void enabledWorkflowUsesConfiguredDatabaseTransactionManagerHistoryAndAsyncOptions() {
        enabledRunner().withPropertyValues("workflow.history-level=full", "workflow.async-executor-activate=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ProcessEngine.class).hasSingleBean(Engine.class);
                    SpringProcessEngineConfiguration configuration = context.getBean(SpringProcessEngineConfiguration.class);
                    DataSource configuredDataSource = configuration.getDataSource();
                    if (configuredDataSource instanceof TransactionAwareDataSourceProxy proxy) {
                        configuredDataSource = proxy.getTargetDataSource();
                    }
                    assertThat(configuredDataSource).isSameAs(context.getBean(DataSource.class));
                    assertThat(configuration.getTransactionManager()).isSameAs(context.getBean(PlatformTransactionManager.class));
                    assertThat(configuration.getHistoryLevel()).isEqualTo(HistoryLevel.FULL);
                    assertThat(configuration.getEngineConfigurations().values()).containsOnly(configuration);
                    assertThat(configuration.isAsyncExecutorActivate()).isFalse();
                    assertThat(configuration.getAsyncExecutor().isActive()).isFalse();
                    assertThat(configuration.getDatabaseSchemaUpdate()).isEqualTo("true");

                    ProcessEngine engine = context.getBean(ProcessEngine.class);
                    engine.getRepositoryService().createDeployment().addString("assembly.bpmn20.xml", PROCESS).deploy();
                    String instanceId = engine.getRuntimeService().startProcessInstanceByKey("assemblyApproval").getId();
                    var task = engine.getTaskService().createTaskQuery().processInstanceId(instanceId).singleResult();
                    assertThat(task.getName()).isEqualTo("Review");
                    engine.getTaskService().complete(task.getId());
                    assertThat(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instanceId).count()).isZero();
                    assertThat(engine.getHistoryService().createHistoricProcessInstanceQuery()
                            .processInstanceId(instanceId).finished().count()).isEqualTo(1);

                    JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
                    jdbc.execute("create table workflow_business_test (id varchar(64) primary key)");
                    new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                        jdbc.update("insert into workflow_business_test (id) values (?)", "rolled-back");
                        engine.getRuntimeService().startProcessInstanceByKey("assemblyApproval", "rolled-back");
                        status.setRollbackOnly();
                    });
                    assertThat(jdbc.queryForObject("select count(*) from workflow_business_test", Integer.class)).isZero();
                    assertThat(engine.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey("rolled-back").count()).isZero();
                    assertThat(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceBusinessKey("rolled-back").count()).isZero();
                });
    }

    @Test
    void historyCanBeExplicitlyDisabled() {
        enabledRunner().withPropertyValues("workflow.history-level=none", "workflow.async-executor-activate=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ProcessEngine.class);
                    SpringProcessEngineConfiguration configuration = context.getBean(SpringProcessEngineConfiguration.class);
                    assertThat(configuration.getHistoryLevel()).isEqualTo(HistoryLevel.NONE);

                    ProcessEngine engine = context.getBean(ProcessEngine.class);
                    engine.getRepositoryService().createDeployment().addString("assembly.bpmn20.xml", PROCESS).deploy();
                    String instanceId = engine.getRuntimeService().startProcessInstanceByKey("assemblyApproval").getId();
                    var task = engine.getTaskService().createTaskQuery().processInstanceId(instanceId).singleResult();
                    assertThat(task).isNotNull();
                    engine.getTaskService().complete(task.getId());
                    assertThat(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instanceId).count()).isZero();
                    assertThat(engine.getHistoryService().createHistoricProcessInstanceQuery()
                            .processInstanceId(instanceId).count()).isZero();
                });
    }

    @Test
    void asyncExecutorCanBeExplicitlyActivated() {
        enabledRunner().withPropertyValues("workflow.async-executor-activate=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ProcessEngine.class);
            SpringProcessEngineConfiguration configuration = context.getBean(SpringProcessEngineConfiguration.class);
            assertThat(configuration.isAsyncExecutorActivate()).isTrue();
            assertThat(configuration.getAsyncExecutor().isActive()).isTrue();
        });
    }

    @Test
    void schemaUpdatesRequireExplicitOptIn() {
        assertThat(new WorkflowProperties().getDatabaseSchemaUpdate()).isEqualTo("false");
        runner().withPropertyValues("workflow.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("no flowable tables");
                });
    }

    private void assertNoWorkflowEngine(AssertableApplicationContext context) {
        assertThat(context).hasNotFailed().doesNotHaveBean(Engine.class)
                .doesNotHaveBean(AbstractEngineConfiguration.class).doesNotHaveBean(AsyncExecutor.class);
        JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name like 'ACT_%'", Integer.class)).isZero();
    }

    private ApplicationContextRunner enabledRunner() {
        return runner().withPropertyValues("workflow.enabled=true", "workflow.database-schema-update=true");
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(TestApplication.class)
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:workflow-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.liquibase.enabled=false", "spring.cloud.nacos.discovery.enabled=false",
                        "spring.cloud.nacos.config.enabled=false", "spring.boot.admin.client.enabled=false",
                        "spring.cloud.compatibility-verifier.enabled=false", "spring.dynamic.tp.enabled=false",
                        "flowable.process.enabled=true", "flowable.async-executor-activate=true",
                        "flowable.app.enabled=true", "flowable.cmmn.enabled=true", "flowable.dmn.enabled=true",
                        "flowable.idm.enabled=true", "flowable.eventregistry.enabled=true");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "com.lotus.bixi.common.core.config.DynamicTpConfiguration",
            "com.lotus.bixi.common.core.config.WebMvcConfiguration"
    })
    static class TestApplication {
    }
}
