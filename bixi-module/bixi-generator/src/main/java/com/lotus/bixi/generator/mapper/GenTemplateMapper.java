

package com.lotus.bixi.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.generator.entity.GenTemplate;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 模板
 *
 * @author tanglei
 * @date 2023-02-21 17:15:44
 */
@Mapper
public interface GenTemplateMapper extends BaseMapper<GenTemplate> {

	/**
	 * 根据groupId查询 模板
	 * @param groupId
	 * @return
	 */
	List<GenTemplate> listTemplateById(Long groupId);

}
