package com.lotus.bixi.generator.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.generator.entity.GenDatasourceConfig;

/**
 * 数据源表
 *
 * @author 唐磊
 * @date 2019-03-31 16:00:20
 */
public interface GenDatasourceConfigService extends IService<GenDatasourceConfig> {

	/**
	 * 保存数据源并且加密
	 * @param config
	 * @return
	 */
	Boolean saveDsByEnc(GenDatasourceConfig config);

	/**
	 * 更新数据源
	 * @param config
	 * @return
	 */
	Boolean updateDsByEnc(GenDatasourceConfig config);

	/**
	 * 更新动态数据的数据源列表
	 * @param datasourceConf
	 * @return
	 */
	void addDynamicDataSource(GenDatasourceConfig datasourceConf);

	/**
	 * 校验数据源配置是否有效
	 * @param datasourceConf 数据源信息
	 * @return 有效/无效
	 */
	Boolean checkDataSource(GenDatasourceConfig datasourceConf);

	/**
	 * 通过数据源名称删除
	 * @param dsIds 数据源ID
	 * @return
	 */
	Boolean removeByDsId(Long[] dsIds);

}
