package com.lotus.bixi.upms.service.local;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.entity.SysLog;
import com.lotus.bixi.upms.api.service.OperationLogService;
import com.lotus.bixi.upms.service.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
@RequiredArgsConstructor
public class LocalOperationLogService implements OperationLogService {

    private final SysLogService logService;

    @Override
    public R<Boolean> saveLog(SysLog sysLog) {
        return R.ok(logService.saveLog(sysLog));
    }

}
