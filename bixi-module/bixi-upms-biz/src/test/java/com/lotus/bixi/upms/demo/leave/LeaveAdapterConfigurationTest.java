package com.lotus.bixi.upms.demo.leave;

import com.lotus.bixi.upms.demo.leave.controller.LeaveRequestController;
import com.lotus.bixi.upms.demo.leave.controller.LeaveWorkflowResultController;
import com.lotus.bixi.upms.demo.leave.local.LocalWorkflowResultReceiver;
import com.lotus.bixi.upms.demo.leave.service.LeaveRequestService;
import com.lotus.bixi.workflow.api.service.WorkflowResultReceiver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class LeaveAdapterConfigurationTest {
    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(Config.class, LeaveRequestService.class, LeaveRequestController.class,
                    LeaveWorkflowResultController.class, LocalWorkflowResultReceiver.class);

    @Test void disabledWorkflowCreatesNoLeaveServicesOrEndpoints() {
        contexts.withPropertyValues("workflow.enabled=false", "bixi.deployment.mode=single").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(LeaveRequestService.class)
                    .doesNotHaveBean(LeaveRequestController.class).doesNotHaveBean(LeaveWorkflowResultController.class)
                    .doesNotHaveBean(WorkflowResultReceiver.class)
                    .doesNotHaveBean(com.lotus.bixi.upms.demo.leave.mapper.LeaveRequestMapper.class);
        });
    }

    @Test void singleUsesLocalReceiverAndDoesNotExposeSpoofableInternalHttpEndpoint() {
        contexts.withPropertyValues("workflow.enabled=true", "bixi.deployment.mode=single").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(WorkflowResultReceiver.class)
                    .hasSingleBean(LocalWorkflowResultReceiver.class).hasSingleBean(LeaveRequestController.class)
                    .doesNotHaveBean(LeaveWorkflowResultController.class);
        });
    }

    @Test void cloudUsesInternalHttpEndpointWithoutRegisteringLocalReceiver() {
        contexts.withPropertyValues("workflow.enabled=true", "bixi.deployment.mode=cloud").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LeaveWorkflowResultController.class)
                    .hasSingleBean(LeaveRequestService.class).doesNotHaveBean(LocalWorkflowResultReceiver.class);
        });
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.mybatis.spring.annotation.MapperScan("com.lotus.bixi.upms.demo.leave.mapper")
    @org.springframework.boot.autoconfigure.ImportAutoConfiguration(com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class)
    static class Config {
        @org.springframework.context.annotation.Bean javax.sql.DataSource dataSource() {
            return new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:leave-adapter-" + java.util.UUID.randomUUID(), "sa", "");
        }
        @org.springframework.context.annotation.Bean org.springframework.transaction.PlatformTransactionManager transactionManager(javax.sql.DataSource source) {
            return new org.springframework.jdbc.datasource.DataSourceTransactionManager(source);
        }
        @org.springframework.context.annotation.Bean com.lotus.bixi.workflow.api.service.WorkflowService workflows() {
            return org.mockito.Mockito.mock(com.lotus.bixi.workflow.api.service.WorkflowService.class);
        }
        @org.springframework.context.annotation.Bean com.lotus.bixi.upms.service.SysUserService users() {
            return org.mockito.Mockito.mock(com.lotus.bixi.upms.service.SysUserService.class);
        }
    }
}
