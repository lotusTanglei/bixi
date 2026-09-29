package com.lotus.bixi.upms.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.mq.reliable.Idempotent;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.upms.api.entity.SysNotice;
import com.lotus.bixi.upms.api.vo.SysNoticeVO;
import com.lotus.bixi.upms.service.SysNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 消息通知管理
 *
 * @author bixi
 * @date 2025-01-01
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/notice")
@Tag(name = "消息通知管理")
public class SysNoticeController {

    private final SysNoticeService sysNoticeService;

    /**
     * 分页查询
     * @param page 分页对象
     * @param sysNotice 消息通知
     * @return
     */
    @Operation(summary = "分页查询", description = "分页查询")
    @GetMapping("/page")
    @HasPermission("sys_notice_view")
    public R getSysNoticePage(Page page, SysNotice sysNotice) {
        return R.ok(sysNoticeService.page(page, Wrappers.query(sysNotice)));
    }


    /**
     * 通过id查询消息通知
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id查询", description = "通过id查询")
    @GetMapping("/{id}")
    @HasPermission("sys_notice_view")
    public R getById(@PathVariable("id") Long id) {
        return R.ok(sysNoticeService.getById(id));
    }

    /**
     * 新增消息通知
     * @param sysNotice 消息通知
     * @return R
     */
    @Operation(summary = "新增消息通知", description = "新增消息通知")
    @SysLog("新增消息通知")
    @Idempotent(scope = "notice.save")
    @PostMapping
    @HasPermission("sys_notice_add")
    public R save(@RequestBody SysNoticeVO sysNotice) {
        return R.ok(sysNoticeService.saveNotice(sysNotice));
    }

    /**
     * 修改消息通知
     * @param sysNotice 消息通知
     * @return R
     */
    @Operation(summary = "修改消息通知", description = "修改消息通知")
    @SysLog("修改消息通知")
    @Idempotent(scope = "notice.update")
    @PutMapping
    @HasPermission("sys_notice_edit")
    public R updateById(@RequestBody SysNoticeVO sysNotice) {
        return sysNoticeService.updateNotice(sysNotice) ? R.ok(Boolean.TRUE) : R.failed("仅草稿通知可修改");
    }

    /**
     * 通过id删除消息通知
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id删除消息通知", description = "通过id删除消息通知")
    @SysLog("通过id删除消息通知")
    @Idempotent(scope = "notice.delete")
    @DeleteMapping("/{id}")
    @HasPermission("sys_notice_del")
    public R removeById(@PathVariable Long id) {
        return R.ok(sysNoticeService.removeById(id));
    }

    /**
     * 发送通知
     * @param id 通知ID
     * @return R
     */
    @Operation(summary = "发送通知", description = "发布草稿或重发已发布通知的实时提醒")
    @SysLog("发送通知")
    @Idempotent(scope = "notice.send")
    @PostMapping("/send/{id}")
    @HasPermission("sys_notice_send")
    public R send(@PathVariable Long id) {
        return sysNoticeService.sendNotice(id) ? R.ok(Boolean.TRUE) : R.failed("仅草稿或已发布通知可发送提醒");
    }

    /**
     * Retry recipient rows that are currently marked FAILED. The original
     * notice and recipient identities are retained so a retry cannot create a
     * second notification or a second recipient row.
     */
    @Operation(summary = "重试通知投递", description = "重试已发布通知的失败收件人投递")
    @SysLog("重试通知投递")
    @Idempotent(scope = "notice.delivery.retry")
    @PostMapping({"/{id}/delivery/retry", "/delivery/retry/{id}"})
    @HasPermission("sys_notice_send")
    public R retryDelivery(@PathVariable Long id) {
        return sysNoticeService.retryNoticeDelivery(id)
                ? R.ok(Boolean.TRUE)
                : R.failed("仅已发布通知可重试投递");
    }

}
