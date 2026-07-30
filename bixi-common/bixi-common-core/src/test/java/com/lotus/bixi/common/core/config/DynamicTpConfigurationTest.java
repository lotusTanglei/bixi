package com.lotus.bixi.common.core.config;

import org.dromara.dynamictp.common.entity.DtpExecutorProps;
import org.dromara.dynamictp.common.queue.VariableLinkedBlockingQueue;
import org.dromara.dynamictp.core.DtpRegistry;
import org.dromara.dynamictp.core.executor.DtpExecutor;
import org.dromara.dynamictp.starter.common.initializer.DtpApplicationContextInitializer;
import org.dromara.dynamictp.starter.common.monitor.DtpEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicTpConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(dynamicTpInitializer())
            .withConfiguration(AutoConfigurations.of(DynamicTpConfiguration.class))
            .withPropertyValues("spring.application.name=bixi-core-test");

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withInitializer(dynamicTpInitializer())
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues("spring.application.name=bixi-core-test");

    @BeforeEach
    void clearRegistryBeforeTest() {
        unregisterTaskExecutor();
    }

    @AfterEach
    void clearRegistryAfterTest() {
        unregisterTaskExecutor();
    }

    @Test
    void createsOneNativeDefaultExecutor() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DtpExecutor.class);
            assertThat(context).hasBean("taskExecutor");
            assertThat(context.getBean("taskExecutor"))
                    .isSameAs(DtpRegistry.getDtpExecutor("taskExecutor"));
        });
    }

    @Test
    void exposesDynamicTpActuatorEndpoint() {
        webContextRunner.run(context -> {
            assertThat(context.getEnvironment()
                    .getProperty("management.endpoints.web.exposure.include"))
                    .isEqualTo("health,info,dynamictp");
            assertThat(context).hasSingleBean(DtpEndpoint.class);
        });
    }

    @Test
    void appliesSafeLocalDefaults() {
        contextRunner.run(context -> {
            DtpExecutor executor = context.getBean("taskExecutor", DtpExecutor.class);

            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(8);
            assertThat(executor.getQueue()).isInstanceOf(VariableLinkedBlockingQueue.class);
            assertThat(executor.getQueue().remainingCapacity()).isEqualTo(1024);
            assertThat(executor.getRejectHandlerType()).isEqualTo("CallerRunsPolicy");
            assertThat(executor.isNotifyEnabled()).isFalse();
            assertThat(executor.isWaitForTasksToCompleteOnShutdown()).isTrue();
            assertThat(executor.getAwaitTerminationSeconds()).isEqualTo(60);
        });
    }

    @Test
    void environmentVariablesOverrideCapacityDefaults() {
        contextRunner
                .withSystemProperties(
                        "BIXI_ASYNC_CORE_POOL_SIZE=3",
                        "BIXI_ASYNC_MAX_POOL_SIZE=6",
                        "BIXI_ASYNC_QUEUE_CAPACITY=2048")
                .run(context -> {
                    DtpExecutor executor = context.getBean("taskExecutor", DtpExecutor.class);

                    assertThat(executor.getCorePoolSize()).isEqualTo(3);
                    assertThat(executor.getMaximumPoolSize()).isEqualTo(6);
                    assertThat(executor.getQueue().remainingCapacity()).isEqualTo(2048);
                });
    }

    @Test
    void unqualifiedAsyncUsesDynamicExecutor() {
        contextRunner.withUserConfiguration(AsyncTestConfiguration.class).run(context -> {
            AsyncProbe probe = context.getBean(AsyncProbe.class);

            assertThat(probe.currentThreadName().get(5, TimeUnit.SECONDS))
                    .startsWith("Bixi-Async-");
        });
    }

    @Test
    void validRefreshUpdatesTheExistingExecutorInPlace() {
        contextRunner.run(context -> {
            DtpExecutor executor = context.getBean("taskExecutor", DtpExecutor.class);

            DtpRegistry.refresh(executorProps(3, 6, 2048));

            assertThat(context.getBean("taskExecutor")).isSameAs(executor);
            assertThat(executor.getCorePoolSize()).isEqualTo(3);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(6);
            assertThat(executor.getQueue().remainingCapacity()).isEqualTo(2048);
        });
    }

    @Test
    void invalidRefreshKeepsTheLastValidConfiguration() {
        contextRunner.run(context -> {
            DtpExecutor executor = context.getBean("taskExecutor", DtpExecutor.class);
            int corePoolSize = executor.getCorePoolSize();
            int maximumPoolSize = executor.getMaximumPoolSize();
            int queueCapacity = executor.getQueue().remainingCapacity();

            DtpRegistry.refresh(executorProps(9, 4, 2048));

            assertThat(executor.getCorePoolSize()).isEqualTo(corePoolSize);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(maximumPoolSize);
            assertThat(executor.getQueue().remainingCapacity()).isEqualTo(queueCapacity);
        });
    }

    private DtpExecutorProps executorProps(int corePoolSize, int maximumPoolSize,
                                          int queueCapacity) {
        DtpExecutorProps props = new DtpExecutorProps();
        props.setThreadPoolName("taskExecutor");
        props.setThreadPoolAliasName("Bixi全局异步线程池");
        props.setThreadNamePrefix("Bixi-Async-");
        props.setCorePoolSize(corePoolSize);
        props.setMaximumPoolSize(maximumPoolSize);
        props.setQueueCapacity(queueCapacity);
        props.setRejectedHandlerType("CallerRunsPolicy");
        props.setNotifyEnabled(false);
        props.setWaitForTasksToCompleteOnShutdown(true);
        props.setAwaitTerminationSeconds(60);
        return props;
    }

    private void unregisterTaskExecutor() {
        if (DtpRegistry.getAllExecutorNames().contains("taskExecutor")) {
            DtpRegistry.unregisterExecutor("taskExecutor");
        }
    }

    @SuppressWarnings("unchecked")
    private static <C extends ConfigurableApplicationContext>
            ApplicationContextInitializer<C> dynamicTpInitializer() {
        return new DtpApplicationContextInitializer();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class AsyncTestConfiguration {

        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }
    }

    static class AsyncProbe {

        @Async
        public CompletableFuture<String> currentThreadName() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }
}
