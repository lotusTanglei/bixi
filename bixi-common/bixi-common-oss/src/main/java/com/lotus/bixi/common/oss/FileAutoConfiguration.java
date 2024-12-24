

package com.lotus.bixi.common.oss;

import com.lotus.bixi.common.oss.core.FileProperties;
import com.lotus.bixi.common.oss.local.LocalFileAutoConfiguration;
import com.lotus.bixi.common.oss.oss.OssAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * aws 自动配置类
 *
 * @author 唐磊
 */
@Import({LocalFileAutoConfiguration.class, OssAutoConfiguration.class})
@EnableConfigurationProperties({FileProperties.class})
public class FileAutoConfiguration {

}
