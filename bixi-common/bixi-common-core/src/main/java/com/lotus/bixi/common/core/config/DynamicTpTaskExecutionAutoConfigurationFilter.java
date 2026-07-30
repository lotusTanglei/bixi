package com.lotus.bixi.common.core.config;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;

/**
 * 使用 DynamicTp 全局执行器时排除 Spring Boot 静态任务执行器
 *
 * @author 唐磊
 */
public class DynamicTpTaskExecutionAutoConfigurationFilter
        implements AutoConfigurationImportFilter {

    private static final String TASK_EXECUTION_AUTO_CONFIGURATION =
            TaskExecutionAutoConfiguration.class.getName();

    @Override
    public boolean[] match(String[] autoConfigurationClasses,
                           AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            matches[i] = !TASK_EXECUTION_AUTO_CONFIGURATION.equals(
                    autoConfigurationClasses[i]);
        }
        return matches;
    }
}
