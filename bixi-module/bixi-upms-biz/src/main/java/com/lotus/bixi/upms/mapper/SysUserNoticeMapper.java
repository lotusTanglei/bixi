package com.lotus.bixi.upms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户消息关联表 Mapper 接口
 *
 * @author bixi
 * @date 2024-05-20
 */
@Mapper
public interface SysUserNoticeMapper extends BaseMapper<SysUserNotice> {

}
