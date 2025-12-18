package com.lotus.bixi.script.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.script.api.entity.ScriptExecutionPlan;
import com.lotus.bixi.script.service.ScriptExecutionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 脚本执行计划管理
 *
 * @author bixi
 * @date 2024-05-20
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/script-execution-plan")
@Tag(name = "脚本执行计划管理")
public class ScriptExecutionPlanController {

    private final ScriptExecutionPlanService scriptExecutionPlanService;

    /**
     * 分页查询
     * @param page 分页对象
     * @param scriptExecutionPlan 脚本执行计划
     * @return
     */
    @Operation(summary = "分页查询", description = "分页查询")
    @GetMapping("/page")
    public R getScriptExecutionPlanPage(Page page, ScriptExecutionPlan scriptExecutionPlan) {
        return R.ok(scriptExecutionPlanService.page(page, Wrappers.query(scriptExecutionPlan)));
    }


    /**
     * 通过id查询脚本执行计划
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id查询", description = "通过id查询")
    @GetMapping("/{id}")
    public R getById(@PathVariable("id") Long id) {
        return R.ok(scriptExecutionPlanService.getById(id));
    }

    /**
     * 新增脚本执行计划
     * @param scriptExecutionPlan 脚本执行计划
     * @return R
     */
    @Operation(summary = "新增脚本执行计划", description = "新增脚本执行计划")
    @SysLog("新增脚本执行计划")
    @PostMapping
    public R save(@RequestBody ScriptExecutionPlan scriptExecutionPlan) {
        return R.ok(scriptExecutionPlanService.save(scriptExecutionPlan));
    }

    /**
     * 修改脚本执行计划
     * @param scriptExecutionPlan 脚本执行计划
     * @return R
     */
    @Operation(summary = "修改脚本执行计划", description = "修改脚本执行计划")
    @SysLog("修改脚本执行计划")
    @PutMapping
    public R updateById(@RequestBody ScriptExecutionPlan scriptExecutionPlan) {
        return R.ok(scriptExecutionPlanService.updateById(scriptExecutionPlan));
    }

    /**
     * 通过id删除脚本执行计划
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id删除脚本执行计划", description = "通过id删除脚本执行计划")
    @SysLog("通过id删除脚本执行计划")
    @DeleteMapping("/{id}")
    public R removeById(@PathVariable Long id) {
        return R.ok(scriptExecutionPlanService.removeById(id));
    }

}
