package com.lotus.bixi.generator.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.generator.entity.GenGroup;
import com.lotus.bixi.generator.entity.GenTemplateGroup;
import com.lotus.bixi.generator.mapper.GenGroupMapper;
import com.lotus.bixi.generator.service.GenGroupService;
import com.lotus.bixi.generator.service.GenTemplateGroupService;
import com.lotus.bixi.generator.util.vo.GroupVO;
import com.lotus.bixi.generator.util.vo.TemplateGroupDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

/**
 * 模板分组
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Slf4j
@Service
@AllArgsConstructor
public class GenGroupServiceImpl extends ServiceImpl<GenGroupMapper, GenGroup> implements GenGroupService {

	private final GenTemplateGroupService genTemplateGroupService;

	/**
	 * 新增模板分组
	 * @param genTemplateGroup
	 */
	@Override
	public void saveGenGroup(TemplateGroupDTO genTemplateGroup) {
		// 1.保存group
		GenGroup group = new GenGroup();
		BeanUtil.copyProperties(genTemplateGroup, group);
		baseMapper.insert(group);
		// 2.保存关系
		List<GenTemplateGroup> goals = new LinkedList<>();
		for (Long TemplateId : genTemplateGroup.getTemplateId()) {
			GenTemplateGroup templateGroup = new GenTemplateGroup();
			templateGroup.setTemplateId(TemplateId).setGroupId(group.getId());
			goals.add(templateGroup);
		}
		genTemplateGroupService.saveBatch(goals);

	}

	/**
	 * 按照ids删除
	 * @param ids groupIds
	 */
	@Override
	public void delGroupAndTemplate(Long[] ids) {
		// 删除分组
		this.removeBatchByIds(CollUtil.toList(ids));
		// 删除关系
		genTemplateGroupService
			.remove(Wrappers.<GenTemplateGroup>lambdaQuery().in(GenTemplateGroup::getGroupId, CollUtil.toList(ids)));
	}

	/**
	 * 按照id查询
	 * @param id
	 * @return
	 */
	@Override
	public GroupVO getGroupVoById(Long id) {
		return baseMapper.getGroupVoById(id);
	}

	/**
	 * 根据id更新
	 * @param groupVo
	 */
	@Override
	public void updateGroupAndTemplateById(GroupVO groupVo) {
		// 1.更新自身
		GenGroup group = new GenGroup();
		BeanUtil.copyProperties(groupVo, group);
		this.updateById(group);
		// 2.更新模板
		// 2.1根据id删除之前的模板
		genTemplateGroupService.remove(
				Wrappers.<GenTemplateGroup>lambdaQuery().eq(GenTemplateGroup::getGroupId, groupVo.getId()));
		// 2.2根据ids创建新的模板分组赋值
		List<GenTemplateGroup> goals = new LinkedList<>();
		for (Long templateId : groupVo.getTemplateId()) {
			goals.add(new GenTemplateGroup().setGroupId(groupVo.getId()).setTemplateId(templateId));
		}
		genTemplateGroupService.saveBatch(goals);
	}

}
