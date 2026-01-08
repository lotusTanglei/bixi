package com.lotus.bixi.script.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.script.api.entity.ScriptExecutionLog;
import com.lotus.bixi.script.service.ScriptExecutionLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 脚本执行记录管理
 *
 * @author bixi
 * @date 2025-01-01
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/script-execution-log")
@Tag(name = "脚本执行记录管理")
public class ScriptExecutionLogController {

    private final ScriptExecutionLogService scriptExecutionLogService;

    /**
     * 分页查询
     * @param page 分页对象
     * @param scriptExecutionLog 脚本执行记录
     * @return
     */
    @Operation(summary = "分页查询", description = "分页查询")
    @GetMapping("/page")
    public R getScriptExecutionLogPage(Page page, ScriptExecutionLog scriptExecutionLog) {
        return R.ok(scriptExecutionLogService.page(page, Wrappers.query(scriptExecutionLog)));
    }


    /**
     * 通过id查询脚本执行记录
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id查询", description = "通过id查询")
    @GetMapping("/{id}")
    public R getById(@PathVariable("id") Long id) {
        return R.ok(scriptExecutionLogService.getById(id));
    }

    /**
     * 新增脚本执行记录
     * @param scriptExecutionLog 脚本执行记录
     * @return R
     */
    @Operation(summary = "新增脚本执行记录", description = "新增脚本执行记录")
    @SysLog("新增脚本执行记录")
    @PostMapping
    public R save(@RequestBody ScriptExecutionLog scriptExecutionLog) {
        return R.ok(scriptExecutionLogService.save(scriptExecutionLog));
    }

    /**
     * 修改脚本执行记录
     * @param scriptExecutionLog 脚本执行记录
     * @return R
     */
    @Operation(summary = "修改脚本执行记录", description = "修改脚本执行记录")
    @SysLog("修改脚本执行记录")
    @PutMapping
    public R updateById(@RequestBody ScriptExecutionLog scriptExecutionLog) {
        return R.ok(scriptExecutionLogService.updateById(scriptExecutionLog));
    }

    /**
     * 通过id删除脚本执行记录
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id删除脚本执行记录", description = "通过id删除脚本执行记录")
    @SysLog("通过id删除脚本执行记录")
    @DeleteMapping("/{id}")
    public R removeById(@PathVariable Long id) {
        return R.ok(scriptExecutionLogService.removeById(id));
    }

}
