package com.lotus.bixi.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.generator.entity.GenTemplateGroup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模板分组关联表
 *
 * @author 唐磊
 * @date 2023-02-22 09:25:15
 */
@Mapper
public interface GenTemplateGroupMapper extends BaseMapper<GenTemplateGroup> {

}
