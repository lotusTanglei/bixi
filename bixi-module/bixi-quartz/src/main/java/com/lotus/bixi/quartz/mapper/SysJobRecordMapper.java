package com.lotus.bixi.quartz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.quartz.entity.SysJobRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务执行日志表
 *
 * @author 唐磊
 * @date 2019-01-27 13:40:20
 */
@Mapper
public interface SysJobRecordMapper extends BaseMapper<SysJobRecord> {

}
