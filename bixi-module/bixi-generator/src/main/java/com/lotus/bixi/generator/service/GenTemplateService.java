

package com.lotus.bixi.generator.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.generator.entity.GenTemplate;
import com.lotus.bixi.common.core.util.R;

/**
 * 模板
 *
 * @author tanglei
 * @date 2023-02-21 17:15:44
 */
public interface GenTemplateService extends IService<GenTemplate> {

	/**
	 * 检查版本
	 * @return {@link R }
	 */
	R checkVersion();

	/**
	 * 在线更新
	 * @return {@link R }
	 */
	R onlineUpdate();

}
