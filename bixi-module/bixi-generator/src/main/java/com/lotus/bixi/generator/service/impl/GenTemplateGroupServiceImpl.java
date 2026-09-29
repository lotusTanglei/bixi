package com.lotus.bixi.generator.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.generator.entity.GenTemplateGroup;
import com.lotus.bixi.generator.mapper.GenTemplateGroupMapper;
import com.lotus.bixi.generator.service.GenTemplateGroupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 模板分组关联表
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Service
@ConditionalOnProperty(prefix = "generator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GenTemplateGroupServiceImpl extends ServiceImpl<GenTemplateGroupMapper, GenTemplateGroup>
		implements GenTemplateGroupService {

}
