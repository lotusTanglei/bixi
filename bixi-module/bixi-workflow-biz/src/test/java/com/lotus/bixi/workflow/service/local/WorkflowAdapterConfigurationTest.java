package com.lotus.bixi.workflow.service.local;

import com.lotus.bixi.workflow.api.feign.RemoteWorkflowService;
import com.lotus.bixi.workflow.api.service.WorkflowService;
import com.lotus.bixi.workflow.service.ProcessInstanceService;
import com.lotus.bixi.workflow.service.WfTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WorkflowAdapterConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class))
            .withUserConfiguration(Adapters.class)
            .withBean(ProcessInstanceService.class, () -> mock(ProcessInstanceService.class))
            .withBean(WfTaskService.class, () -> mock(WfTaskService.class))
            .withPropertyValues("spring.cloud.openfeign.client.config.remoteWorkflowService.url=http://workflow.invalid");

    @ParameterizedTest
    @CsvSource({"single,true", "cloud,true", "single,false", "cloud,false"})
    void onlyTheEnabledModeRegistersItsAdapter(String mode, boolean enabled) {
        runner.withPropertyValues("bixi.deployment.mode=" + mode, "workflow.enabled=" + enabled)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    if (!enabled) {
                        assertThat(context).doesNotHaveBean(WorkflowService.class);
                    } else if (mode.equals("single")) {
                        assertThat(context).hasSingleBean(WorkflowService.class);
                        assertThat(context).hasBean("localWorkflowService");
                        assertThat(context).doesNotHaveBean(RemoteWorkflowService.class);
                        String beanName = context.getBeanNamesForType(WorkflowService.class)[0];
                        assertThat(context.getBeanFactory().getBeanDefinition(beanName).isPrimary()).isTrue();
                    } else {
                        assertThat(context).hasSingleBean(WorkflowService.class);
                        assertThat(context).hasSingleBean(RemoteWorkflowService.class);
                        assertThat(context).doesNotHaveBean("localWorkflowService");
                    }
                });
    }

    @Test
    void missingEnableFlagRegistersNeitherAdapter() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(WorkflowService.class);
        });
    }

    @Test
    void defaultDeploymentUsesTheCloudAdapterWhenEnabled() {
        runner.withPropertyValues("workflow.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RemoteWorkflowService.class);
            assertThat(context).doesNotHaveBean("localWorkflowService");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "com.lotus.bixi.workflow.service.local", excludeFilters =
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Test.*"))
    @EnableFeignClients(basePackages = "com.lotus.bixi.workflow.api.feign")
    static class Adapters {
    }
}
