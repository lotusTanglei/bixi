package com.lotus.bixi.generator.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.generator.entity.GenGroup;
import com.lotus.bixi.generator.util.vo.GroupVO;
import com.lotus.bixi.generator.util.vo.TemplateGroupDTO;

/**
 * 模板分组
 *
 * @author 唐磊
 * @date 2025-01-01
 */
public interface GenGroupService extends IService<GenGroup> {

	void saveGenGroup(TemplateGroupDTO genTemplateGroup);

	/**
	 * 删除分组极其关系
	 * @param ids
	 */
	void delGroupAndTemplate(Long[] ids);

	/**
	 * 查询group数据
	 * @param id
	 */
	GroupVO getGroupVoById(Long id);

	/**
	 * 更新group数据
	 * @param GroupVo
	 */
	void updateGroupAndTemplateById(GroupVO GroupVo);

}
