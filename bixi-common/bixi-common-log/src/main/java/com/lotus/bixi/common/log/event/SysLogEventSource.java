package com.lotus.bixi.common.log.event;

import com.lotus.bixi.upms.api.entity.SysLog;
import lombok.Data;

/**
 * spring event log
 *
 * @author 唐磊
 * @date 2023/8/11
 */
@Data
public class SysLogEventSource extends SysLog {

    /**
     * 参数重写成object
     */
    private Object body;

}
