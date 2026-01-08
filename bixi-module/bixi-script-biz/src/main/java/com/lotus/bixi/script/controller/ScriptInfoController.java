package com.lotus.bixi.script.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.script.api.entity.ScriptInfo;
import com.lotus.bixi.script.service.ScriptInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 脚本主信息管理
 *
 * @author bixi
 * @date 2025-01-01
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/script-info")
@Tag(name = "脚本主信息管理")
public class ScriptInfoController {

    private final ScriptInfoService scriptInfoService;

    /**
     * 分页查询
     * @param page 分页对象
     * @param scriptInfo 脚本主信息
     * @return
     */
    @Operation(summary = "分页查询", description = "分页查询")
    @GetMapping("/page")
    public R getScriptInfoPage(Page page, ScriptInfo scriptInfo) {
        return R.ok(scriptInfoService.page(page, Wrappers.query(scriptInfo)));
    }


    /**
     * 通过id查询脚本主信息
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id查询", description = "通过id查询")
    @GetMapping("/{id}")
    public R getById(@PathVariable("id") Long id) {
        return R.ok(scriptInfoService.getById(id));
    }

    /**
     * 新增脚本主信息
     * @param scriptInfo 脚本主信息
     * @return R
     */
    @Operation(summary = "新增脚本主信息", description = "新增脚本主信息")
    @SysLog("新增脚本主信息")
    @PostMapping
    public R save(@RequestBody ScriptInfo scriptInfo) {
        return R.ok(scriptInfoService.save(scriptInfo));
    }

    /**
     * 修改脚本主信息
     * @param scriptInfo 脚本主信息
     * @return R
     */
    @Operation(summary = "修改脚本主信息", description = "修改脚本主信息")
    @SysLog("修改脚本主信息")
    @PutMapping
    public R updateById(@RequestBody ScriptInfo scriptInfo) {
        return R.ok(scriptInfoService.updateById(scriptInfo));
    }

    /**
     * 通过id删除脚本主信息
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id删除脚本主信息", description = "通过id删除脚本主信息")
    @SysLog("通过id删除脚本主信息")
    @DeleteMapping("/{id}")
    public R removeById(@PathVariable Long id) {
        return R.ok(scriptInfoService.removeById(id));
    }

}
