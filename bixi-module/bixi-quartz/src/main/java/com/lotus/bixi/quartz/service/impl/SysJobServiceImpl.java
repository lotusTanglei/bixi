package com.lotus.bixi.quartz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.mapper.SysJobMapper;
import com.lotus.bixi.quartz.service.SysJobService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 定时任务调度表
 *
 * @author 唐磊
 * @date 2019-01-27 10:04:42
 */
@Slf4j
@Service
@AllArgsConstructor
public class SysJobServiceImpl extends ServiceImpl<SysJobMapper, SysJob> implements SysJobService {

}
