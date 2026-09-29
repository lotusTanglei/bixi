package com.lotus.bixi.common.ai.config;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Applies the AI opt-in before the DashScope starter can create provider
 * clients or embedding engines. This also covers auto-configurations added by
 * a newer Spring AI Alibaba starter.
 */
public class AiAutoConfigurationImportFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    private static final String DASHSCOPE_PREFIX =
            "com.alibaba.cloud.ai.autoconfigure.dashscope.";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean[] match(String[] autoConfigurationClasses,
                           AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean enabled = "true".equalsIgnoreCase(environment.getProperty("ai.enabled"));
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            String className = autoConfigurationClasses[i];
            matches[i] = className == null || enabled || !className.startsWith(DASHSCOPE_PREFIX);
        }
        return matches;
    }
}
