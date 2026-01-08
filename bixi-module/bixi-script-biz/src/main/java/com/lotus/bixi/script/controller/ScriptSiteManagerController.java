package com.lotus.bixi.script.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.script.api.entity.ScriptSiteManager;
import com.lotus.bixi.script.service.ScriptSiteManagerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 现场负责人管理
 *
 * @author bixi
 * @date 2025-01-01
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/script-site-manager")
@Tag(name = "现场负责人管理")
public class ScriptSiteManagerController {

    private final ScriptSiteManagerService scriptSiteManagerService;

    /**
     * 分页查询
     * @param page 分页对象
     * @param scriptSiteManager 现场负责人
     * @return
     */
    @Operation(summary = "分页查询", description = "分页查询")
    @GetMapping("/page")
    public R getScriptSiteManagerPage(Page page, ScriptSiteManager scriptSiteManager) {
        return R.ok(scriptSiteManagerService.page(page, Wrappers.query(scriptSiteManager)));
    }


    /**
     * 通过id查询现场负责人
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id查询", description = "通过id查询")
    @GetMapping("/{id}")
    public R getById(@PathVariable("id") Long id) {
        return R.ok(scriptSiteManagerService.getById(id));
    }

    /**
     * 新增现场负责人
     * @param scriptSiteManager 现场负责人
     * @return R
     */
    @Operation(summary = "新增现场负责人", description = "新增现场负责人")
    @SysLog("新增现场负责人")
    @PostMapping
    public R save(@RequestBody ScriptSiteManager scriptSiteManager) {
        return R.ok(scriptSiteManagerService.save(scriptSiteManager));
    }

    /**
     * 修改现场负责人
     * @param scriptSiteManager 现场负责人
     * @return R
     */
    @Operation(summary = "修改现场负责人", description = "修改现场负责人")
    @SysLog("修改现场负责人")
    @PutMapping
    public R updateById(@RequestBody ScriptSiteManager scriptSiteManager) {
        return R.ok(scriptSiteManagerService.updateById(scriptSiteManager));
    }

    /**
     * 通过id删除现场负责人
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id删除现场负责人", description = "通过id删除现场负责人")
    @SysLog("通过id删除现场负责人")
    @DeleteMapping("/{id}")
    public R removeById(@PathVariable Long id) {
        return R.ok(scriptSiteManagerService.removeById(id));
    }

}
