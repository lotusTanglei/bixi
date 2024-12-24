

package com.lotus.bixi.common.oss.local;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地文件 配置信息
 *
 * @author 唐磊
 * <p>
 * bucket 设置公共读权限
 */
@Data
@ConfigurationProperties(prefix = "local")
public class LocalFileProperties {

    /**
     * 是否开启
     */
    private boolean enable;

    /**
     * 默认路径
     */
    private String basePath;

}
