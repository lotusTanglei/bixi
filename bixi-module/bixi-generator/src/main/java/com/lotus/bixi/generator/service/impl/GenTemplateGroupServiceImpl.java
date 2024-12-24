
package com.lotus.bixi.generator.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.generator.entity.GenTemplateGroup;
import com.lotus.bixi.generator.mapper.GenTemplateGroupMapper;
import com.lotus.bixi.generator.service.GenTemplateGroupService;
import org.springframework.stereotype.Service;

/**
 * 模板分组关联表
 *
 * @author tanglei
 * @date 2023-02-22 09:25:15
 */
@Service
public class GenTemplateGroupServiceImpl extends ServiceImpl<GenTemplateGroupMapper, GenTemplateGroup>
		implements GenTemplateGroupService {

}
