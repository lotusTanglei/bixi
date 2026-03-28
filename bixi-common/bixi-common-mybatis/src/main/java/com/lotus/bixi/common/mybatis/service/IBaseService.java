package com.lotus.bixi.common.mybatis.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 基础服务接口，封装通用的 CRUD 操作
 * <p>
 * 所有业务服务接口应继承此接口，获得基础 CRUD 能力
 * </p>
 *
 * @param <T> 实体类型
 * @author Bixi
 * @since 0.0.3
 */
public interface IBaseService<T> extends IService<T> {

	/**
	 * 获取 BaseMapper
	 * @return BaseMapper
	 */
	BaseMapper<T> getBaseMapper();

}
