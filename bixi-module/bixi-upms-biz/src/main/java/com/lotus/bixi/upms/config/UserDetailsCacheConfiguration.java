package com.lotus.bixi.upms.config;

import com.lotus.bixi.common.core.constant.CacheConstants;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Refresh configuration-dependent authorities before UPMS is ready in either deployment mode. */
@Configuration(proxyBeanMethods = false)
public class UserDetailsCacheConfiguration {
    @Bean
    ApplicationRunner invalidateUserDetailsOnStartup(CacheManager cacheManager) {
        return args -> {
            // Redis survives restarts, but cached permissions reflect the previous workflow switch.
            // Invalidate both username and phone entries without touching authorizations or sessions.
            Cache cache = cacheManager.getCache(CacheConstants.USER_DETAILS);
            if (cache != null) cache.clear();
        };
    }
}
