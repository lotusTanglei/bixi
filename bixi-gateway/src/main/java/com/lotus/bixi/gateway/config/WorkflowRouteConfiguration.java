package com.lotus.bixi.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "workflow.enabled", havingValue = "true")
public class WorkflowRouteConfiguration {
    @Bean
    RouteLocator workflowRoutes(RouteLocatorBuilder builder) {
        return builder.routes().route("bixi-workflow", route -> route.order(-10)
                .path("/admin/workflow/**").uri("lb://bixi-workflow-biz")).build();
    }
}
