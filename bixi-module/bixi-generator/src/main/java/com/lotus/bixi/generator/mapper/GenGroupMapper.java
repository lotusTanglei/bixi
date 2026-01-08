package com.lotus.bixi.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.generator.entity.GenGroup;
import com.lotus.bixi.generator.util.vo.GroupVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 模板分组
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Mapper
public interface GenGroupMapper extends BaseMapper<GenGroup> {

	GroupVO getGroupVoById(@Param("id") Long id);

}
