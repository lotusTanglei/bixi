package com.lotus.bixi.generator.config;

import lombok.Data;
import org.anyline.util.ConfigTable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 代码生成默认配置类
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Data
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = BixiGeneratorDefaultProperties.PREFIX)
public class BixiGeneratorDefaultProperties implements InitializingBean {

	public static final String PREFIX = "generator";

	/**
	 * Whether the generator module is assembled. A dedicated generator process
	 * remains enabled by default; single mode supplies an explicit false value
	 * unless it is intentionally requested.
	 */
	private boolean enabled = true;

	/**
	 * 是否开启在线更新
	 */
	private boolean autoCheckVersion = false;

	/**
	 * 模板项目地址
	 */
	private String onlineUrl = "";

	/**
	 * 在线模板来源允许的主机名
	 */
	private List<String> onlineAllowedHosts = List.of();

	/**
	 * 在线模板固定的完整 Git revision
	 */
	private String onlineRevision = "";

	/**
	 * 固定 revision 下的 manifest 相对路径
	 */
	private String onlineManifestPath = "manifest.json";

	/**
	 * manifest 的小写 SHA-256
	 */
	private String onlineManifestSha256 = "";

	private int onlineMaxFiles = 64;

	private int onlineMaxManifestBytes = 64 * 1024;

	private int onlineMaxFileBytes = 1024 * 1024;

	/**
	 * 生成代码的包名
	 */
	private String packageName = "com.lotus.bixi.upms";

	/**
	 * 生成代码的版本
	 */
	private String version = "1.0.0";

	/**
	 * 生成代码的模块名
	 */
	private String moduleName = "admin";

	/**
	 * 生成代码的后端路径
	 */
	private String backendPath = "bixi-module/bixi-upms-biz";

	/**
	 * 生成代码的 API 模块路径（项目内相对路径）
	 */
	private String apiPath = "bixi-module/bixi-upms-api";

	/**
	 * 生成代码的前端路径
	 */
	private String frontendPath = "bixi-ui";

	/**
	 * 生成文件所属项目根目录；相对路径按进程工作目录解析
	 */
	private String projectRoot = ".";

	/**
	 * 允许写入的项目内一级目录
	 */
	private List<String> allowedOutputRoots = List.of("bixi-module", "bixi-ui", "bixi-project-documents/sql");

	/**
	 * 生成代码的作者
	 */
	private String author = "bixi";

	/**
	 * 生成代码的邮箱
	 */
	private String email = "bixi@lotus-studio.top";

	/**
	 * 表单布局（一列、两列）
	 */
	private Integer formLayout = 2;

	/**
	 * 下载方式 （0 文件下载、1写入目录）
	 */
	private String generatorType = "0";

	@Override
	public void afterPropertiesSet() throws Exception {
		ConfigTable.KEEP_ADAPTER = 0;
	}

}
