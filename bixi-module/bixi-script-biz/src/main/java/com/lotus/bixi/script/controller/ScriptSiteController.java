package com.lotus.bixi.script.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.script.api.entity.ScriptSite;
import com.lotus.bixi.script.service.ScriptSiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 现场信息管理
 *
 * @author bixi
 * @date 2025-01-01
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/script-site")
@Tag(name = "现场信息管理")
public class ScriptSiteController {

    private final ScriptSiteService scriptSiteService;

    /**
     * 分页查询
     * @param page 分页对象
     * @param scriptSite 现场信息
     * @return
     */
    @Operation(summary = "分页查询", description = "分页查询")
    @GetMapping("/page")
    public R getScriptSitePage(Page page, ScriptSite scriptSite) {
        return R.ok(scriptSiteService.page(page, Wrappers.query(scriptSite)));
    }


    /**
     * 通过id查询现场信息
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id查询", description = "通过id查询")
    @GetMapping("/{id}")
    public R getById(@PathVariable("id") Long id) {
        return R.ok(scriptSiteService.getById(id));
    }

    /**
     * 新增现场信息
     * @param scriptSite 现场信息
     * @return R
     */
    @Operation(summary = "新增现场信息", description = "新增现场信息")
    @SysLog("新增现场信息")
    @PostMapping
    public R save(@RequestBody ScriptSite scriptSite) {
        return R.ok(scriptSiteService.save(scriptSite));
    }

    /**
     * 修改现场信息
     * @param scriptSite 现场信息
     * @return R
     */
    @Operation(summary = "修改现场信息", description = "修改现场信息")
    @SysLog("修改现场信息")
    @PutMapping
    public R updateById(@RequestBody ScriptSite scriptSite) {
        return R.ok(scriptSiteService.updateById(scriptSite));
    }

    /**
     * 通过id删除现场信息
     * @param id id
     * @return R
     */
    @Operation(summary = "通过id删除现场信息", description = "通过id删除现场信息")
    @SysLog("通过id删除现场信息")
    @DeleteMapping("/{id}")
    public R removeById(@PathVariable Long id) {
        return R.ok(scriptSiteService.removeById(id));
    }

}
