package com.lotus.bixi.script.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.script.api.entity.ScriptTask;
import com.lotus.bixi.script.mapper.ScriptTaskMapper;
import com.lotus.bixi.script.service.ScriptTaskService;
import org.springframework.stereotype.Service;

/**
 * 任务表 服务实现类
 *
 * @author bixi
 * @date 2024-05-20
 */
@Service
public class ScriptTaskServiceImpl extends ServiceImpl<ScriptTaskMapper, ScriptTask> implements ScriptTaskService {

}
