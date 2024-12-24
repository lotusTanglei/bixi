

package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.upms.api.dto.SysLogDTO;
import com.lotus.bixi.upms.api.entity.SysLog;

import java.util.List;

/**
 * <p>
 * 日志表 服务类
 * </p>
 *
 * @author 唐磊
 * @since 2017-11-20
 */
public interface SysLogService extends IService<SysLog> {

    /**
     * 分页查询日志
     *
     * @param page
     * @param sysLog
     * @return
     */
    Page getLogByPage(Page page, SysLogDTO sysLog);

    /**
     * 插入日志
     *
     * @param sysLog 日志对象
     * @return true/false
     */
    Boolean saveLog(SysLog sysLog);

    /**
     * 查询日志列表
     *
     * @param sysLog 查询条件
     * @return List<SysLog>
     */
    List<SysLog> getList(SysLogDTO sysLog);

}
