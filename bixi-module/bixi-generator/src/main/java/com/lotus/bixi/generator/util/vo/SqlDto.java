package com.lotus.bixi.generator.util.vo;

import lombok.Data;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@Data
public class SqlDto {

	/**
	 * 数据源ID
	 */
	private String dsName;

	/**
	 * sql脚本
	 */
	private String sql;

}
