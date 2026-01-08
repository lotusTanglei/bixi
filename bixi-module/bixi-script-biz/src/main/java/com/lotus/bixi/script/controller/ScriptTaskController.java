package com.lotus.bixi.script.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.script.api.entity.ScriptTask;
import com.lotus.bixi.script.service.ScriptTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 任务管理
 *
 * @author bixi
 * @date 2025-01-01
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/script-task")
@Tag(name = "任务管理")
public class ScriptTaskController {

    private final ScriptTaskService scriptTaskService;

    /**
     * 分页查询
     * @param page 分页对象
     * @param scriptTask 任务
     * @return
     */
    @Operation(summary = "分页查询", description = "分页查询")
    @GetMapping("/page")
    public R getScriptTaskPage(Page page, ScriptTask scriptTask) {
        return R.ok(scriptTaskService.page(page, Wrappers.query(scriptTask)));
    }


    /**
     * 通过id查询任务
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id查询", description = "通过id查询")
    @GetMapping("/{id}")
    public R getById(@PathVariable("id") Long id) {
        return R.ok(scriptTaskService.getById(id));
    }

    /**
     * 新增任务
     * @param scriptTask 任务
     * @return R
     */
    @Operation(summary = "新增任务", description = "新增任务")
    @SysLog("新增任务")
    @PostMapping
    public R save(@RequestBody ScriptTask scriptTask) {
        return R.ok(scriptTaskService.save(scriptTask));
    }

    /**
     * 修改任务
     * @param scriptTask 任务
     * @return R
     */
    @Operation(summary = "修改任务", description = "修改任务")
    @SysLog("修改任务")
    @PutMapping
    public R updateById(@RequestBody ScriptTask scriptTask) {
        return R.ok(scriptTaskService.updateById(scriptTask));
    }

    /**
     * 通过id删除任务
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id删除任务", description = "通过id删除任务")
    @SysLog("通过id删除任务")
    @DeleteMapping("/{id}")
    public R removeById(@PathVariable Long id) {
        return R.ok(scriptTaskService.removeById(id));
    }

}
