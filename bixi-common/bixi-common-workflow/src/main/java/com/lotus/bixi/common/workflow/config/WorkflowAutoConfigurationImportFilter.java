package com.lotus.bixi.common.workflow.config;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Applies Bixi's opt-in before the starter can create engines, schemas or job executors.
 * Keeping this at import time also covers optional engines added by another dependency.
 */
public class WorkflowAutoConfigurationImportFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    private static final String FLOWABLE_PREFIX = "org.flowable.spring.boot.";

    private static final List<String> UNUSED_ENGINE_PREFIXES = List.of("app.", "cmmn.", "dmn.", "idm.",
            "eventregistry.", "ldap.");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        // Match @ConditionalOnProperty(havingValue = "true") exactly.
        boolean enabled = "true".equalsIgnoreCase(environment.getProperty("workflow.enabled"));
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            String className = autoConfigurationClasses[i];
            matches[i] = className == null || !className.startsWith(FLOWABLE_PREFIX)
                    || enabled && UNUSED_ENGINE_PREFIXES.stream()
                            .noneMatch(prefix -> className.startsWith(FLOWABLE_PREFIX + prefix));
        }
        return matches;
    }
}
