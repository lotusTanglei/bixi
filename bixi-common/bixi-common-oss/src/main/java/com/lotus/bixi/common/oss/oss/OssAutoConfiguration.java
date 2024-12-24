

package com.lotus.bixi.common.oss.oss;

import com.lotus.bixi.common.oss.core.FileProperties;
import com.lotus.bixi.common.oss.core.FileTemplate;
import com.lotus.bixi.common.oss.oss.http.OssEndpoint;
import com.lotus.bixi.common.oss.oss.service.OssTemplate;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * aws 自动配置类
 *
 * @author 唐磊
 */
@AllArgsConstructor
public class OssAutoConfiguration {

    private final FileProperties properties;

    @Bean
    @Primary
    @ConditionalOnMissingBean(OssTemplate.class)
    @ConditionalOnProperty(name = "file.oss.enable", havingValue = "true")
    public FileTemplate ossTemplate() {
        return new OssTemplate(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "file.oss.info", havingValue = "true")
    public OssEndpoint ossEndpoint(OssTemplate template) {
        return new OssEndpoint(template);
    }

}
