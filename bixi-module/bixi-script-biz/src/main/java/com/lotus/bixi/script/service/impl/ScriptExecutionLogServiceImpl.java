package com.lotus.bixi.script.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.script.api.entity.ScriptExecutionLog;
import com.lotus.bixi.script.mapper.ScriptExecutionLogMapper;
import com.lotus.bixi.script.service.ScriptExecutionLogService;
import org.springframework.stereotype.Service;

/**
 * 脚本执行记录表 服务实现类
 *
 * @author bixi
 * @date 2024-05-20
 */
@Service
public class ScriptExecutionLogServiceImpl extends ServiceImpl<ScriptExecutionLogMapper, ScriptExecutionLog> implements ScriptExecutionLogService {

}
