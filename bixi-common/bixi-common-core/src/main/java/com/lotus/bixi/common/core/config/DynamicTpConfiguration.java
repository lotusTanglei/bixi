package com.lotus.bixi.common.core.config;

import com.lotus.bixi.common.core.factory.YamlPropertySourceFactory;
import org.dromara.dynamictp.spring.annotation.EnableDynamicTp;
import org.dromara.dynamictp.starter.common.DtpBootBeanConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.PropertySource;

/**
 * DynamicTp 全局异步线程池配置
 *
 * @author 唐磊
 */
@EnableDynamicTp
@AutoConfiguration
@AutoConfigureBefore(DtpBootBeanConfiguration.class)
@PropertySource(value = "classpath:dynamic-tp-config.yml", factory = YamlPropertySourceFactory.class)
public class DynamicTpConfiguration {

}
