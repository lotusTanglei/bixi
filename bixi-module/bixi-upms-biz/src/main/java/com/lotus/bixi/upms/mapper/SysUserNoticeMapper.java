package com.lotus.bixi.upms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.api.vo.UserNoticeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户消息关联表 Mapper 接口
 *
 * @author bixi
 * @date 2025-01-01
 */
@Mapper
public interface SysUserNoticeMapper extends BaseMapper<SysUserNotice> {

    /**
     * 分页查询用户通知
     * @param page 分页对象
     * @param userNotice 查询条件
     * @return
     */
    IPage<UserNoticeVO> selectUserNoticePage(Page page, @Param("query") UserNoticeVO userNotice);

    /**
     * 通过ID查询用户通知
     * @param id ID
     * @return
     */
    UserNoticeVO selectUserNoticeById(Long id);

}
