package com.lotus.bixi;

import com.lotus.bixi.common.workflow.config.WorkflowAutoConfiguration;
import com.lotus.bixi.workflow.WorkflowApplication;
import com.lotus.bixi.workflow.api.feign.RemoteWorkflowService;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.boot.ProcessEngineAutoConfiguration;
import org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** Loads the real single application.yml with workflow-biz present on its classpath. */
class WorkflowSingleConfigurationTest {

    @Test
    void singleConfigurationWinsOverDependencyApplicationFilesAndDefaultsWorkflowOff() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(WorkflowImports.class)
                // Skip development infrastructure credentials; use actual application.yml defaults.
                .withPropertyValues("spring.profiles.active=workflow-assembly-test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getProperty("bixi.deployment.mode")).isEqualTo("single");
                    assertThat(context.getEnvironment().getProperty("workflow.enabled", Boolean.class)).isFalse();
                    assertThat(context.getEnvironment().getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class)).isFalse();
                    assertThat(context.getEnvironment().getProperty("spring.cloud.nacos.config.enabled", Boolean.class)).isFalse();
                    assertThat(context).doesNotHaveBean(WorkflowApplication.class);
                    assertThat(context).doesNotHaveBean(RemoteWorkflowService.class);
                    assertThat(context).doesNotHaveBean(ProcessEngine.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(WorkflowApplication.class)
    @ImportAutoConfiguration({WorkflowAutoConfiguration.class, ProcessEngineAutoConfiguration.class,
            ProcessEngineServicesAutoConfiguration.class})
    static class WorkflowImports {
    }
}
