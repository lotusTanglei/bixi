package com.lotus.bixi.quartz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.quartz.entity.SysJobRecord;
import com.lotus.bixi.quartz.mapper.SysJobRecordMapper;
import com.lotus.bixi.quartz.service.SysJobRecordService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 定时任务执行日志表
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Slf4j
@Service
@AllArgsConstructor
public class SysJobRecordServiceImpl extends ServiceImpl<SysJobRecordMapper, SysJobRecord> implements SysJobRecordService {

}
