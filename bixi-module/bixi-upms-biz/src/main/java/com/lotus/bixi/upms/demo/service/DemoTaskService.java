package com.lotus.bixi.upms.demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.upms.demo.dto.DemoTaskQuery;
import com.lotus.bixi.upms.demo.entity.DemoTask;

public interface DemoTaskService extends IService<DemoTask> {

    Page<DemoTask> pageTasks(Page<DemoTask> page, DemoTaskQuery query);

}
