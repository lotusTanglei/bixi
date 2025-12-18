package com.lotus.bixi.upms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.upms.api.entity.SysNotice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息通知表 Mapper 接口
 *
 * @author bixi
 * @date 2024-05-20
 */
@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {

}
