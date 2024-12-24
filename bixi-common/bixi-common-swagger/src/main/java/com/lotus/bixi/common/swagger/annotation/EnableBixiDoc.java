

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
