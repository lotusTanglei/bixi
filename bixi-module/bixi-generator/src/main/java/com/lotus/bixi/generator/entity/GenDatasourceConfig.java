package com.lotus.bixi.generator.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源表
 *
 * @author tanglei
 * @date 2019-03-31 16:00:20
 */
@Data
@TableName("gen_datasource_config")
@EqualsAndHashCode(callSuper = true)
public class GenDatasourceConfig extends BaseEntity<GenDatasourceConfig> {

	/**
	 * 名称
	 */
	private String name;

	/**
	 * 数据库类型
	 */
	private String dsType;

	/**
	 * 配置类型 （0 主机形式 | 1 url形式）
	 */
	private Integer configType;

	/**
	 * 主机地址
	 */
	private String host;

	/**
	 * 端口
	 */
	private Integer port;

	/**
	 * jdbc-url
	 */
	private String url;

	/**
	 * 实例
	 */
	private String instance;

	/**
	 * 数据库名称
	 */
	private String dsName;

	/**
	 * 用户名
	 */
	private String username;

	/**
	 * 密码
	 */
	private String password;

}
