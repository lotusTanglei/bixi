

package com.lotus.bixi.upms.service.impl;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.upms.api.dto.SysLogDTO;
import com.lotus.bixi.upms.api.entity.SysLog;
import com.lotus.bixi.upms.mapper.SysLogMapper;
import com.lotus.bixi.upms.service.SysLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 日志表 服务实现类
 * </p>
 *
 * @author 唐磊
 * @since 2017-11-20
 */
@Service
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements SysLogService {

    @Override
    public Page getLogByPage(Page page, SysLogDTO sysLog) {
        return baseMapper.selectPage(page, buildQuery(sysLog));
    }

    /**
     * 插入日志
     *
     * @param sysLog 日志对象
     * @return true/false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveLog(SysLog sysLog) {
        baseMapper.insert(sysLog);
        return Boolean.TRUE;
    }

    /**
     * 查询日志列表
     *
     * @param sysLog 查询条件
     * @return List<SysLog>
     */
    @Override
    public List<SysLog> getList(SysLogDTO sysLog) {
        return baseMapper.selectList(buildQuery(sysLog));
    }

    /**
     * 构建查询条件
     *
     * @param sysLog 前端条件
     * @return LambdaQueryWrapper
     */
    private LambdaQueryWrapper buildQuery(SysLogDTO sysLog) {
        LambdaQueryWrapper<SysLog> wrapper = Wrappers.lambdaQuery();
        if (StrUtil.isNotBlank(sysLog.getType())) {
            wrapper.eq(SysLog::getType, sysLog.getType());
        }

        if (ArrayUtil.isNotEmpty(sysLog.getCreateTime())) {
            wrapper.ge(SysLog::getCreateTime, sysLog.getCreateTime()[0])
                    .le(SysLog::getCreateTime, sysLog.getCreateTime()[1]);
        }

        return wrapper;
    }

}
