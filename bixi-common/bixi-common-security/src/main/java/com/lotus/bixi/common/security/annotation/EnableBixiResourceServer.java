

package com.lotus.bixi.common.security.annotation;

import com.lotus.bixi.common.security.component.BixiResourceServerAutoConfiguration;
import com.lotus.bixi.common.security.component.BixiResourceServerConfiguration;
import com.lotus.bixi.common.security.feign.BixiFeignClientConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author 唐磊
 * @date 2022-06-04
 * <p>
 * 资源服务注解
 */
@Documented
@Inherited
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Import({BixiResourceServerAutoConfiguration.class, BixiResourceServerConfiguration.class,
        BixiFeignClientConfiguration.class})
public @interface EnableBixiResourceServer {

}
