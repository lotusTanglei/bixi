package com.lotus.bixi.upms.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
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
 * @date 2024-05-20
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
    @PostMapping
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
    @PutMapping
    public R updateById(@RequestBody SysNoticeVO sysNotice) {
        return R.ok(sysNoticeService.updateNotice(sysNotice));
    }

    /**
     * 通过id删除消息通知
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id删除消息通知", description = "通过id删除消息通知")
    @SysLog("通过id删除消息通知")
    @DeleteMapping("/{id}")
    public R removeById(@PathVariable Long id) {
        return R.ok(sysNoticeService.removeById(id));
    }

    /**
     * 发送通知
     * @param id 通知ID
     * @return R
     */
    @Operation(summary = "发送通知", description = "发送通知")
    @SysLog("发送通知")
    @PostMapping("/send/{id}")
    public R send(@PathVariable Long id) {
        return R.ok(sysNoticeService.sendNotice(id));
    }

}
