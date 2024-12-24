

package com.lotus.bixi.common.oss.core;

import com.lotus.bixi.common.oss.local.LocalFileProperties;
import com.lotus.bixi.common.oss.oss.OssProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件 配置信息
 *
 * @author 唐磊
 * <p>
 * bucket 设置公共读权限
 */
@Data
@ConfigurationProperties(prefix = "file")
public class FileProperties {

    /**
     * 默认的存储桶名称
     */
    private String bucket = "local";

    /**
     * 本地文件配置信息
     */
    private LocalFileProperties local;

    /**
     * oss 文件配置信息
     */
    private OssProperties oss;

}
