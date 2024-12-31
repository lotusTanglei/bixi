package com.lotus.bixi.generator.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 动态查询
 *
 * @author 唐磊
 * @date 2022-07-09
 */
@Mapper
public interface GenDynamicMapper {

	@InterceptorIgnore(tenantLine = "true")
	List<LinkedHashMap<String, Object>> dynamicQuerySql(@Param("value") String sq);

}
