package com.lotus.bixi.script.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.script.api.entity.ScriptTaskReminder;
import com.lotus.bixi.script.service.ScriptTaskReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 任务提醒管理
 *
 * @author bixi
 * @date 2025-01-01
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/script-task-reminder")
@Tag(name = "任务提醒管理")
public class ScriptTaskReminderController {

    private final ScriptTaskReminderService scriptTaskReminderService;

    /**
     * 分页查询
     * @param page 分页对象
     * @param scriptTaskReminder 任务提醒
     * @return
     */
    @Operation(summary = "分页查询", description = "分页查询")
    @GetMapping("/page")
    public R getScriptTaskReminderPage(Page page, ScriptTaskReminder scriptTaskReminder) {
        return R.ok(scriptTaskReminderService.page(page, Wrappers.query(scriptTaskReminder)));
    }


    /**
     * 通过id查询任务提醒
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id查询", description = "通过id查询")
    @GetMapping("/{id}")
    public R getById(@PathVariable("id") Long id) {
        return R.ok(scriptTaskReminderService.getById(id));
    }

    /**
     * 新增任务提醒
     * @param scriptTaskReminder 任务提醒
     * @return R
     */
    @Operation(summary = "新增任务提醒", description = "新增任务提醒")
    @SysLog("新增任务提醒")
    @PostMapping
    public R save(@RequestBody ScriptTaskReminder scriptTaskReminder) {
        return R.ok(scriptTaskReminderService.save(scriptTaskReminder));
    }

    /**
     * 修改任务提醒
     * @param scriptTaskReminder 任务提醒
     * @return R
     */
    @Operation(summary = "修改任务提醒", description = "修改任务提醒")
    @SysLog("修改任务提醒")
    @PutMapping
    public R updateById(@RequestBody ScriptTaskReminder scriptTaskReminder) {
        return R.ok(scriptTaskReminderService.updateById(scriptTaskReminder));
    }

    /**
     * 通过id删除任务提醒
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id删除任务提醒", description = "通过id删除任务提醒")
    @SysLog("通过id删除任务提醒")
    @DeleteMapping("/{id}")
    public R removeById(@PathVariable Long id) {
        return R.ok(scriptTaskReminderService.removeById(id));
    }

}
