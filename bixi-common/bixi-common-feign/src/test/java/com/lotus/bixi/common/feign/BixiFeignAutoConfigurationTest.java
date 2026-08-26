package com.lotus.bixi.common.feign;

import com.lotus.bixi.common.feign.core.BixiFeignInnerRequestInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class BixiFeignAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BixiFeignAutoConfiguration.class));

    @Test
    void disablesFeignInfrastructureInSingleMode() {
        contextRunner.withPropertyValues("bixi.deployment.mode=single")
                .run(context -> assertThat(context).doesNotHaveBean(BixiFeignInnerRequestInterceptor.class));
    }

    @Test
    void enablesFeignInfrastructureInCloudMode() {
        contextRunner.withPropertyValues("bixi.deployment.mode=cloud")
                .run(context -> assertThat(context).hasSingleBean(BixiFeignInnerRequestInterceptor.class));
    }

}
