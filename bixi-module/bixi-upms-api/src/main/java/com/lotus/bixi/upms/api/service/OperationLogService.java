package com.lotus.bixi.upms.api.service;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.entity.SysLog;

public interface OperationLogService {

    R<Boolean> saveLog(SysLog sysLog);

}
