

package com.lotus.bixi.common.log;

import com.lotus.bixi.common.log.aspect.SysLogAspect;
import com.lotus.bixi.common.log.config.BixiLogProperties;
import com.lotus.bixi.common.log.event.SysLogListener;
import com.lotus.bixi.upms.api.feign.RemoteLogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@EnableAsync
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BixiLogProperties.class)
@ConditionalOnProperty(value = "security.log.enabled", matchIfMissing = true)
public class LogAutoConfiguration {

    @Bean
    public SysLogListener sysLogListener(BixiLogProperties logProperties, RemoteLogService remoteLogService) {
        return new SysLogListener(remoteLogService, logProperties);
    }

    @Bean
    public SysLogAspect sysLogAspect() {
        return new SysLogAspect();
    }

}
