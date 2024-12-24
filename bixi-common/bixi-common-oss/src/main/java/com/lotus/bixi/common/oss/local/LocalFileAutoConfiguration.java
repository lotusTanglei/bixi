

package com.lotus.bixi.common.oss.local;

import com.lotus.bixi.common.oss.core.FileProperties;
import com.lotus.bixi.common.oss.core.FileTemplate;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * aws 自动配置类
 *
 * @author 唐磊
 */
@AllArgsConstructor
public class LocalFileAutoConfiguration {

    private final FileProperties properties;

    @Bean
    @ConditionalOnMissingBean(LocalFileTemplate.class)
    @ConditionalOnProperty(name = "file.local.enable", havingValue = "true", matchIfMissing = true)
    public FileTemplate localFileTemplate() {
        return new LocalFileTemplate(properties);
    }

}
