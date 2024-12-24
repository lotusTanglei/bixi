

package com.lotus.bixi.common.log.event;

import com.lotus.bixi.upms.api.entity.SysLog;
import org.springframework.context.ApplicationEvent;

/**
 * @author 唐磊 系统日志事件
 */
public class SysLogEvent extends ApplicationEvent {

    public SysLogEvent(SysLog source) {
        super(source);
    }

}
