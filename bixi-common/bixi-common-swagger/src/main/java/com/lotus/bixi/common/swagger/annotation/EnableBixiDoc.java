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

package com.lotus.bixi.common.swagger.annotation;

import com.lotus.bixi.common.core.factory.YamlPropertySourceFactory;
import com.lotus.bixi.common.swagger.config.OpenAPIDefinitionImportSelector;
import com.lotus.bixi.common.swagger.support.SwaggerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

import java.lang.annotation.*;

/**
 * 开启 bixi spring doc
 *
 * @author 唐磊
 * @date 2022-03-26
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@EnableConfigurationProperties(SwaggerProperties.class)
@Import(OpenAPIDefinitionImportSelector.class)
@PropertySource(value = "classpath:openapi-config.yml", factory = YamlPropertySourceFactory.class)
public @interface EnableBixiDoc {

    /**
     * 网关路由前缀
     *
     * @return String
     */
    String value();

    /**
     * 是否是微服务架构
     *
     * @return true
     */
    boolean isMicro() default true;

}
