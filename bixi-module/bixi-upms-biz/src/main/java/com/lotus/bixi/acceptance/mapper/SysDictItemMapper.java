package com.lotus.bixi.acceptance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.common.mybatis.annotation.DataScope;
import com.lotus.bixi.acceptance.api.entity.SysDictItem;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository("acceptanceSysDictItemMapper")
@DataScope(userAlias = "", userColumn = "create_by", creatorScope = true)
public interface SysDictItemMapper extends BaseMapper<SysDictItem> {
}
