/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & www.dreamlu.net).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lotus.bixi.common.xss;

import com.lotus.bixi.common.xss.config.BixiXssProperties;
import com.lotus.bixi.common.xss.core.DefaultXssCleaner;
import com.lotus.bixi.common.xss.core.FormXssClean;
import com.lotus.bixi.common.xss.core.JacksonXssClean;
import com.lotus.bixi.common.xss.core.XssCleaner;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * jackson xss 配置
 *
 * @author 唐磊
 */
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(BixiXssProperties.class)
@ConditionalOnProperty(prefix = BixiXssProperties.PREFIX, name = "enabled",
        havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BixiXssAutoConfiguration implements WebMvcConfigurer {

    private final BixiXssProperties xssProperties;

    @Bean
    @ConditionalOnMissingBean
    public XssCleaner xssCleaner(BixiXssProperties properties) {
        return new DefaultXssCleaner(properties);
    }

    @Bean
    public FormXssClean formXssClean(BixiXssProperties properties,
                                     XssCleaner xssCleaner) {
        return new FormXssClean(properties, xssCleaner);
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer xssJacksonCustomizer(
            BixiXssProperties properties, XssCleaner xssCleaner) {
        return builder -> builder.deserializerByType(String.class, new JacksonXssClean(properties, xssCleaner));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        List<String> patterns = xssProperties.getPathPatterns();
        if (patterns.isEmpty()) {
            patterns.add("/**");
        }
        com.lotus.bixi.common.xss.core.XssCleanInterceptor interceptor = new com.lotus.bixi.common.xss.core.XssCleanInterceptor(
                xssProperties);
        registry.addInterceptor(interceptor)
                .addPathPatterns(patterns)
                .excludePathPatterns(xssProperties.getPathExcludePatterns())
                .order(Ordered.LOWEST_PRECEDENCE);
    }

}
