package com.lotus.bixi.gateway.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRouteConfigurationTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void routesExistOnlyWhenWorkflowIsEnabled(boolean enabled) {
        new ApplicationContextRunner().withUserConfiguration(Config.class, WorkflowRouteConfiguration.class)
                .withPropertyValues("workflow.enabled=" + enabled).run(context -> {
                    assertThat(context).hasNotFailed();
                    if (!enabled) { assertThat(context).doesNotHaveBean("workflowRoutes"); return; }
                    var routes = context.getBean("workflowRoutes", RouteLocator.class).getRoutes().collectList().block();
                    assertThat(routes).singleElement().satisfies(route -> {
                        assertThat(route.getUri().toString()).isEqualTo("lb://bixi-workflow-biz");
                        assertThat(route.getOrder()).isLessThan(0);
                    });
                });
    }
    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean RouteLocatorBuilder routeLocatorBuilder(ConfigurableApplicationContext context) { return new RouteLocatorBuilder(context); }
        @Bean PathRoutePredicateFactory pathRoutePredicateFactory() { return new PathRoutePredicateFactory(); }
    }
}
