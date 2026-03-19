package com.lotus.bixi.common.log.event;

import com.lotus.bixi.upms.api.entity.SysLog;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * spring event log
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysLogEventSource extends SysLog {

    /**
     * 参数重写成object
     */
    private Object body;

}
