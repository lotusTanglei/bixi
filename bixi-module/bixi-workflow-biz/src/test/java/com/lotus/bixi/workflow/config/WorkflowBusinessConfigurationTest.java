package com.lotus.bixi.workflow.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.common.workflow.config.WorkflowAutoConfiguration;
import com.lotus.bixi.common.feign.annotation.EnableBixiFeignClients;
import com.lotus.bixi.workflow.WorkflowApplication;
import com.lotus.bixi.workflow.api.feign.RemoteWorkflowService;
import com.lotus.bixi.workflow.controller.TaskController;
import com.lotus.bixi.workflow.mapper.WfProcessInstanceMapper;
import com.lotus.bixi.workflow.service.ProcessInstanceService;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.boot.ProcessEngineAutoConfiguration;
import org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests actual component and MyBatis scanning without an engine or database. */
class WorkflowBusinessConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BusinessScan.class);

    @Test
    void singleDoesNotImportTheCloudEntryPoint() {
        new ApplicationContextRunner().withUserConfiguration(WorkflowApplication.class)
                .withPropertyValues("bixi.deployment.mode=single", "workflow.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(WorkflowApplication.class);
                    assertThat(context).doesNotHaveBean("org.springframework.cloud.openfeign.FeignAutoConfiguration");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"single", "cloud"})
    void enabledWorkflowWiresTheSameBusinessImplementationWithARealEngine(String mode) {
        runner.withUserConfiguration(TestDatabase.class)
                .withPropertyValues("bixi.deployment.mode=" + mode, "workflow.enabled=true",
                        "workflow.database-schema-update=true", "workflow.async-executor-activate=false",
                        "flowable.check-process-definitions=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProcessEngine.class);
                    assertThat(context).hasSingleBean(ProcessInstanceService.class);
                    assertThat(context).hasSingleBean(WfProcessInstanceMapper.class);
                    assertThat(context.getBeansWithAnnotation(Mapper.class)).hasSize(9);
                    assertThat(context).hasSingleBean(TaskController.class);
                    assertThat(context).doesNotHaveBean(WorkflowApplication.class);
                });
    }

    @Test
    void disabledCloudDoesNotRegisterWorkflowFeignClient() {
        new ApplicationContextRunner().withUserConfiguration(TestFeignScan.class)
                .withPropertyValues("bixi.deployment.mode=cloud", "workflow.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RemoteWorkflowService.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"single", "cloud"})
    void disabledWorkflowDoesNotRequireAnEngineOrRegisterBusinessBeans(String mode) {
        runner.withPropertyValues("bixi.deployment.mode=" + mode, "workflow.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("processInstanceController");
                    assertThat(context).doesNotHaveBean("processInstanceServiceImpl");
                    assertThat(context).doesNotHaveBean("wfProcessInstanceMapper");
                    assertThat(context).doesNotHaveBean("processEndListener");
                    assertThat(context).doesNotHaveBean("globalEventListener");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"single", "cloud"})
    void workflowIsOptIn(String mode) {
        runner.withPropertyValues("bixi.deployment.mode=" + mode).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("taskController");
            assertThat(context).doesNotHaveBean("wfTaskServiceImpl");
            assertThat(context).doesNotHaveBean("wfApprovalRecordMapper");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "com.lotus.bixi.workflow", excludeFilters = {
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WorkflowApplication.class),
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Test.*")
    })
    @MapperScan("com.lotus.bixi.workflow.mapper")
    static class BusinessScan {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableBixiFeignClients(basePackages = "com.lotus.bixi.workflow.api.feign")
    static class TestFeignScan {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @ImportAutoConfiguration({com.lotus.bixi.common.core.config.JacksonConfiguration.class,
            org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
            WorkflowAutoConfiguration.class, ProcessEngineAutoConfiguration.class,
            ProcessEngineServicesAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
    static class TestDatabase {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:workflow-business-" + UUID.randomUUID()
                    + ";DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
