package com.lotus.bixi.upms.demo.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.upms.demo.dto.DemoTaskQuery;
import com.lotus.bixi.upms.demo.entity.DemoTask;
import com.lotus.bixi.upms.demo.mapper.DemoTaskMapper;
import com.lotus.bixi.upms.demo.service.DemoTaskService;
import org.springframework.stereotype.Service;

@Service
public class DemoTaskServiceImpl extends ServiceImpl<DemoTaskMapper, DemoTask> implements DemoTaskService {

    @Override
    public Page<DemoTask> pageTasks(Page<DemoTask> page, DemoTaskQuery query) {
        LambdaQueryWrapper<DemoTask> wrapper = Wrappers.lambdaQuery();
        wrapper.like(StrUtil.isNotBlank(query.getTitle()), DemoTask::getTitle, query.getTitle())
                .like(StrUtil.isNotBlank(query.getAssignee()), DemoTask::getAssignee, query.getAssignee())
                .eq(StrUtil.isNotBlank(query.getPriority()), DemoTask::getPriority, query.getPriority())
                .eq(StrUtil.isNotBlank(query.getTaskStatus()), DemoTask::getTaskStatus, query.getTaskStatus())
                .orderByDesc(DemoTask::getCreateTime);
        return page(page, wrapper);
    }

}
