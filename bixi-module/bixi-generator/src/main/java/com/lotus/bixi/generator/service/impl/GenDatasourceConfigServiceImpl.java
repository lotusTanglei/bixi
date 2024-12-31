package com.lotus.bixi.generator.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.creator.DataSourceCreator;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.generator.entity.GenDatasourceConfig;
import com.lotus.bixi.generator.mapper.GenDatasourceConfigMapper;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.common.datasource.enums.DsConfTypeEnum;
import com.lotus.bixi.common.datasource.enums.DsJdbcUrlEnum;
import com.lotus.bixi.generator.service.GenDatasourceConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 数据源表
 *
 * @author 唐磊
 * @date 2019-03-31 16:00:20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenDatasourceConfigServiceImpl extends ServiceImpl<GenDatasourceConfigMapper, GenDatasourceConfig>
		implements GenDatasourceConfigService {

	private final StringEncryptor stringEncryptor;

	private final DataSourceCreator druidDataSourceCreator;

	/**
	 * 保存数据源并且加密
	 * @param config
	 * @return
	 */
	@Override
	public Boolean saveDsByEnc(GenDatasourceConfig config) {
		// 校验配置合法性
		if (!checkDataSource(config)) {
			return Boolean.FALSE;
		}

		// 添加动态数据源
		addDynamicDataSource(config);

		// 更新数据库配置
		config.setPassword(stringEncryptor.encrypt(config.getPassword()));
		this.baseMapper.insert(config);
		return Boolean.TRUE;
	}

	/**
	 * 更新数据源
	 * @param config 数据源信息
	 * @return
	 */
	@Override
	public Boolean updateDsByEnc(GenDatasourceConfig config) {
		if (!checkDataSource(config)) {
			return Boolean.FALSE;
		}
		// 先移除
		DynamicRoutingDataSource dynamicRoutingDataSource = SpringContextHolder.getBean(DynamicRoutingDataSource.class);
		dynamicRoutingDataSource.removeDataSource(baseMapper.selectById(config.getId()).getName());

		// 再添加
		addDynamicDataSource(config);

		// 更新数据库配置
		if (StrUtil.isNotBlank(config.getPassword())) {
			config.setPassword(stringEncryptor.encrypt(config.getPassword()));
		}
		this.baseMapper.updateById(config);
		return Boolean.TRUE;
	}

	/**
	 * 通过数据源名称删除
	 * @param dsIds 数据源ID
	 * @return
	 */
	@Override
	public Boolean removeByDsId(Long[] dsIds) {
		DynamicRoutingDataSource dynamicRoutingDataSource = SpringContextHolder.getBean(DynamicRoutingDataSource.class);
		this.baseMapper.selectByIds(CollUtil.toList(dsIds))
			.forEach(ds -> dynamicRoutingDataSource.removeDataSource(ds.getName()));
		this.baseMapper.deleteByIds(CollUtil.toList(dsIds));
		return Boolean.TRUE;
	}

	/**
	 * 添加动态数据源
	 * @param config 数据源信息
	 */
	@Override
	public void addDynamicDataSource(GenDatasourceConfig config) {
		DataSourceProperty dataSourceProperty = new DataSourceProperty();
		dataSourceProperty.setPoolName(config.getName());
		dataSourceProperty.setUrl(config.getUrl());
		dataSourceProperty.setUsername(config.getUsername());
		dataSourceProperty.setPassword(config.getPassword());
		DataSource dataSource = druidDataSourceCreator.createDataSource(dataSourceProperty);

		DynamicRoutingDataSource dynamicRoutingDataSource = SpringContextHolder.getBean(DynamicRoutingDataSource.class);
		dynamicRoutingDataSource.addDataSource(dataSourceProperty.getPoolName(), dataSource);
	}

	/**
	 * 校验数据源配置是否有效
	 * @param config 数据源信息
	 * @return 有效/无效
	 */
	@Override
	public Boolean checkDataSource(GenDatasourceConfig config) {
		String url;
		// JDBC 配置形式
		if (DsConfTypeEnum.JDBC.getType().equals(config.getConfigType())) {
			url = config.getUrl();
		}
		else if (DsJdbcUrlEnum.MSSQL.getDbName().equals(config.getDsType())) {
			// 主机形式 sql server 特殊处理
			DsJdbcUrlEnum urlEnum = DsJdbcUrlEnum.get(config.getDsType());
			url = String.format(urlEnum.getUrl(), config.getHost(), config.getPort(), config.getDsName());
		}
		else {
			DsJdbcUrlEnum urlEnum = DsJdbcUrlEnum.get(config.getDsType());
			url = String.format(urlEnum.getUrl(), config.getHost(), config.getPort(), config.getDsName());
		}

		config.setUrl(url);

		try (Connection connection = DriverManager.getConnection(url, config.getUsername(), config.getPassword())) {
		}
		catch (SQLException e) {
			log.error("数据源配置 {} , 获取链接失败", config.getName(), e);
			throw new RuntimeException("数据库配置错误，链接失败");
		}
		return Boolean.TRUE;
	}

}
