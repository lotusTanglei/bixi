package com.lotus.bixi.upms.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.util.SecurityUtils;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.api.vo.UserNoticeVO;
import com.lotus.bixi.upms.service.SysUserNoticeService;
import com.lotus.bixi.upms.sse.UserNoticeSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 用户消息关联管理
 *
 * @author bixi
 * @date 2025-01-01
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/user-notice")
@Tag(name = "用户消息关联管理")
public class SysUserNoticeController {

    private final SysUserNoticeService sysUserNoticeService;
    private final UserNoticeSseService userNoticeSseService;

    /**
     * 分页查询
     * @param page 分页对象
     * @param sysUserNotice 用户消息关联
     * @return
     */
    @Operation(summary = "分页查询", description = "分页查询")
    @GetMapping("/page")
    public R getSysUserNoticePage(Page page, UserNoticeVO userNoticeVO) {
        Long userId = SecurityUtils.getUser().getId();
        userNoticeVO.setUserId(userId);
        return R.ok(sysUserNoticeService.getUserNoticePage(page, userNoticeVO));
    }


    /**
     * 分页查询通知发送记录
     * @param page 分页对象
     * @param userNoticeVO 用户消息关联
     * @return
     */
    @Operation(summary = "分页查询通知发送记录", description = "分页查询通知发送记录")
    @GetMapping("/record/page")
    public R getNoticeRecordPage(Page page, UserNoticeVO userNoticeVO) {
        return R.ok(sysUserNoticeService.getUserNoticePage(page, userNoticeVO));
    }

    /**
     * 通过id查询用户消息关联
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id查询", description = "通过id查询")
    @GetMapping("/{id}")
    public R getById(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getUser().getId();
        UserNoticeVO entity = sysUserNoticeService.getUserNoticeById(id);
        if (entity == null || !Objects.equals(entity.getUserId(), userId)) {
            return R.failed("记录不存在");
        }
        return R.ok(entity);
    }

    /**
     * 新增用户消息关联
     * @param sysUserNotice 用户消息关联
     * @return R
     */
    @Operation(summary = "新增用户消息关联", description = "新增用户消息关联")
    @SysLog("新增用户消息关联")
    @PostMapping
    public R save(@RequestBody SysUserNotice sysUserNotice) {
        Long userId = SecurityUtils.getUser().getId();
        sysUserNotice.setUserId(userId);
        return R.ok(sysUserNoticeService.save(sysUserNotice));
    }

    /**
     * 修改用户消息关联
     * @param sysUserNotice 用户消息关联
     * @return R
     */
    @Operation(summary = "修改用户消息关联", description = "修改用户消息关联")
    @SysLog("修改用户消息关联")
    @PutMapping
    public R updateById(@RequestBody SysUserNotice sysUserNotice) {
        Long userId = SecurityUtils.getUser().getId();
        if (sysUserNotice.getId() == null) {
            return R.failed("id不能为空");
        }
        if ("1".equals(sysUserNotice.getIsRead())) {
            return R.ok(sysUserNoticeService.markRead(sysUserNotice.getId(), userId));
        }
        return R.ok(Boolean.FALSE);
    }

    /**
     * 通过id删除用户消息关联
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id删除用户消息关联", description = "通过id删除用户消息关联")
    @SysLog("通过id删除用户消息关联")
    @DeleteMapping("/{id}")
    public R removeById(@PathVariable Long id) {
        Long userId = SecurityUtils.getUser().getId();
        return R.ok(sysUserNoticeService.deleteOne(id, userId));
    }

    @Operation(summary = "全部已读", description = "全部已读")
    @SysLog("全部已读")
    @PutMapping("/read/all")
    public R readAll() {
        Long userId = SecurityUtils.getUser().getId();
        return R.ok(sysUserNoticeService.markAllRead(userId));
    }

    @Operation(summary = "全部删除", description = "全部删除")
    @SysLog("全部删除")
    @DeleteMapping("/delete/all")
    public R deleteAll() {
        Long userId = SecurityUtils.getUser().getId();
        return R.ok(sysUserNoticeService.deleteAll(userId));
    }

    @Operation(summary = "消息SSE订阅", description = "消息SSE订阅")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        Long userId = SecurityUtils.getUser().getId();
        return userNoticeSseService.subscribe(userId);
    }
}
