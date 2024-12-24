

package com.lotus.bixi.common.feign;

import com.alibaba.cloud.sentinel.feign.SentinelFeignAutoConfiguration;
import com.lotus.bixi.common.feign.core.BixiFeignInnerRequestInterceptor;
import com.lotus.bixi.common.feign.core.BixiFeignRequestCloseInterceptor;
import com.lotus.bixi.common.feign.sentinel.ext.BixiSentinelFeign;
import feign.Feign;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.BixiFeignClientsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;

/**
 * sentinel 配置
 *
 * @author 唐磊
 * @date 2020-02-12
 */
@Configuration(proxyBeanMethods = false)
@Import(BixiFeignClientsRegistrar.class)
@AutoConfigureBefore(SentinelFeignAutoConfiguration.class)
public class BixiFeignAutoConfiguration {

    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "feign.sentinel.enabled")
    public Feign.Builder feignSentinelBuilder() {
        return BixiSentinelFeign.builder();
    }

    /**
     * add http connection close header
     *
     * @return
     */
    @Bean
    public BixiFeignRequestCloseInterceptor bixiFeignRequestCloseInterceptor() {
        return new BixiFeignRequestCloseInterceptor();
    }

    /**
     * add inner request header
     */
    @Bean
    public BixiFeignInnerRequestInterceptor bixiFeignInnerRequestInterceptor() {
        return new BixiFeignInnerRequestInterceptor();
    }

}
