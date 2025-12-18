package com.lotus.bixi.upms.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.service.SysUserNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户消息关联管理
 *
 * @author bixi
 * @date 2024-05-20
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/user-notice")
@Tag(name = "用户消息关联管理")
public class SysUserNoticeController {

    private final SysUserNoticeService sysUserNoticeService;

    /**
     * 分页查询
     * @param page 分页对象
     * @param sysUserNotice 用户消息关联
     * @return
     */
    @Operation(summary = "分页查询", description = "分页查询")
    @GetMapping("/page")
    public R getSysUserNoticePage(Page page, SysUserNotice sysUserNotice) {
        return R.ok(sysUserNoticeService.page(page, Wrappers.query(sysUserNotice)));
    }


    /**
     * 通过id查询用户消息关联
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id查询", description = "通过id查询")
    @GetMapping("/{id}")
    public R getById(@PathVariable("id") Long id) {
        return R.ok(sysUserNoticeService.getById(id));
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
        return R.ok(sysUserNoticeService.updateById(sysUserNotice));
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
        return R.ok(sysUserNoticeService.removeById(id));
    }

}
