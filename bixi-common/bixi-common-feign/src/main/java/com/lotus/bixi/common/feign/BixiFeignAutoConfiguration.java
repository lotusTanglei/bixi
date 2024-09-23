/*
 * Copyright (c) 2020 pig4cloud Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    public BixiFeignRequestCloseInterceptor pigFeignRequestCloseInterceptor() {
        return new BixiFeignRequestCloseInterceptor();
    }

    /**
     * add inner request header
     *
     * @return PigFeignInnerRequestInterceptor
     */
    @Bean
    public BixiFeignInnerRequestInterceptor pigFeignInnerRequestInterceptor() {
        return new BixiFeignInnerRequestInterceptor();
    }

}
