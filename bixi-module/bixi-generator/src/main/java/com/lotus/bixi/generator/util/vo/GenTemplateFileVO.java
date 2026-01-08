package com.lotus.bixi.generator.util.vo;

import lombok.Data;

/**
 * @author 唐磊
 * @date 2025-01-01
 * <p>
 * CGTM 文件路径
 * <p>
 * { "templateName": "Controller", "generatorPath":
 * "${backendPath}/src/main/java/${packagePath}/${moduleName}/controller/${ClassName}Controller.java",
 * "templateDesc": "后台Controller", "templateFile": "temps/Controller" },
 */
@Data
public class GenTemplateFileVO {

	/**
	 * 模板名称
	 */
	private String templateName;

	/**
	 * 路径
	 */
	private String generatorPath;

	/**
	 * 模板 desc
	 */
	private String templateDesc;

	/**
	 * 模板文件
	 */
	private String templateFile;

}
