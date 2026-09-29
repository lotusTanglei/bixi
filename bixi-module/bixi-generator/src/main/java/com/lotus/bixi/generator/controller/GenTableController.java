package com.lotus.bixi.generator.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.generator.dto.GenTableImportRequest;
import com.lotus.bixi.generator.dto.GenTableImportResult;
import com.lotus.bixi.generator.entity.GenTable;
import com.lotus.bixi.generator.entity.GenTableColumn;
import com.lotus.bixi.generator.service.GenTableColumnService;
import com.lotus.bixi.generator.service.GenTableService;
import com.pig4cloud.plugin.excel.annotation.ResponseExcel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 列属性
 *
 * @author 唐磊x code generator
 * @date 2025-01-01
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "generator", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/table")
@Tag(description = "table", name = "列属性管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class GenTableController {

	private final GenTableColumnService tableColumnService;

	private final GenTableService tableService;

	/**
	 * 分页查询
	 * @param page 分页对象
	 * @param table 列属性
	 * @return
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@HasPermission("codegen_table_view")
	public R getTablePage(Page page, GenTable table) {
		return R.ok(tableService.queryTablePage(page, table));
	}

	/**
	 * 通过id查询表信息（代码生成设置 + 表 + 字段设置）
	 * @param id id
	 * @return R
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/{id}")
	@HasPermission("codegen_table_view")
	public R getTable(@PathVariable("id") Long id) {
		return R.ok(tableService.getById(id));
	}

	/**
	 * 查询数据源所有表
	 * @param dsName 数据源
	 */
	@GetMapping("/list/{dsName}")
	@HasPermission("codegen_table_view")
	public R listTable(@PathVariable("dsName") String dsName) {
		return R.ok(tableService.queryTableList(dsName));
	}

	/**
	 * 获取表信息
	 * @param dsName 数据源
	 * @param tableName 表名称
	 */
	@PostMapping("/{dsName}/{tableName}")
	@HasPermission("codegen_table_edit")
	@SysLog("初始化代码生成表配置")
	public R<GenTable> getTable(@PathVariable("dsName") String dsName, @PathVariable String tableName) {
		return R.ok(tableService.queryOrBuildTable(dsName, tableName));
	}

	/**
	 * 精确查询已导入的生成配置，不触发导入
	 * @param dsName 数据源
	 * @param tableName 表名称
	 */
	@GetMapping("/config/{dsName}/{tableName}")
	@HasPermission("codegen_table_view")
	public R<GenTable> getConfiguredTable(@PathVariable("dsName") String dsName,
			@PathVariable("tableName") String tableName) {
		return R.ok(tableService.findConfiguredTableDetails(dsName, tableName));
	}

	/**
	 * 显式导入物理表配置；仅新建时写入归属标记
	 * @param dsName 数据源
	 * @param tableName 表名称
	 * @param request 导入归属
	 */
	@PostMapping("/import/{dsName}/{tableName}")
	@HasPermission("codegen_table_edit")
	@SysLog("导入代码生成表")
	public R<GenTableImportResult> importTable(@PathVariable("dsName") String dsName,
			@PathVariable("tableName") String tableName, @Valid @RequestBody GenTableImportRequest request) {
		return R.ok(tableService.importTable(dsName, tableName, request.author()));
	}

	/**
	 * 查询表DDL语句
	 * @param dsName 数据源
	 * @param tableName 表名称
	 */
	@GetMapping("/column/{dsName}/{tableName}")
	@HasPermission("codegen_table_view")
	public R getColumn(@PathVariable("dsName") String dsName, @PathVariable String tableName) throws Exception {
		return R.ok(tableService.queryTableColumn(dsName, tableName));
	}

	/**
	 * 查询表DDL语句
	 * @param dsName 数据源
	 * @param tableName 表名称
	 */
	@GetMapping("/ddl/{dsName}/{tableName}")
	@HasPermission("codegen_table_view")
	public R getDdl(@PathVariable("dsName") String dsName, @PathVariable String tableName) throws Exception {
		return R.ok(tableService.queryTableDdl(dsName, tableName));
	}

	/**
	 * 同步表信息
	 * @param dsName 数据源
	 * @param tableName 表名称
	 */
	@PostMapping("/sync/{dsName}/{tableName}")
	@HasPermission("codegen_table_sync")
	@SysLog("同步代码生成表结构")
	public R<GenTable> syncTable(@PathVariable("dsName") String dsName, @PathVariable String tableName) {
		return R.ok(tableService.syncTable(dsName, tableName));
	}

	/**
	 * 修改列属性
	 * @param table 列属性
	 * @return R
	 */
	@Operation(summary = "修改列属性", description = "修改列属性")
	@SysLog("修改列属性")
	@HasPermission("codegen_table_edit")
	@PutMapping
	public R updateById(@RequestBody GenTable table) {
		return R.ok(tableService.updateById(table));
	}

	/**
	 * 修改表字段数据
	 * @param dsName 数据源
	 * @param tableName 表名称
	 * @param tableFieldList 字段列表
	 */
	@PutMapping("/field/{dsName}/{tableName}")
	@HasPermission("codegen_table_edit")
	@SysLog("修改代码生成字段")
	public R<String> updateTableField(@PathVariable("dsName") String dsName, @PathVariable String tableName,
			@RequestBody List<GenTableColumn> tableFieldList) {
		tableColumnService.updateTableField(dsName, tableName, tableFieldList);
		return R.ok();
	}

	/**
	 * 导出excel 表格
	 * @param table 查询条件
	 * @return excel 文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@HasPermission("codegen_table_export")
	public List<GenTable> export(GenTable table) {
		return tableService.list(Wrappers.query(table));
	}

}
