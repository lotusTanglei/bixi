

package com.lotus.bixi.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.generator.entity.GenGroup;
import com.lotus.bixi.generator.util.vo.GroupVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 模板分组
 *
 * @author tanglei
 * @date 2023-02-21 20:01:53
 */
@Mapper
public interface GenGroupMapper extends BaseMapper<GenGroup> {

	GroupVO getGroupVoById(@Param("id") Long id);

}
