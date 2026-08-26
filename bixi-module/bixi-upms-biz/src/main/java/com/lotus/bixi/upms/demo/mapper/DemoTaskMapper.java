package com.lotus.bixi.upms.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.upms.demo.entity.DemoTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DemoTaskMapper extends BaseMapper<DemoTask> {
}
