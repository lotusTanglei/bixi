package com.lotus.bixi.generator.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.generator.entity.GenFieldType;
import com.lotus.bixi.generator.service.GenFieldTypeService;
import com.pig4cloud.plugin.excel.annotation.ResponseExcel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/fieldtype")
@Tag(description = "fieldtype", name = "列属性管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class GenFieldTypeController {

	private final GenFieldTypeService fieldTypeService;

	/**
	 * 分页查询
	 * @param page 分页对象
	 * @param fieldType 列属性
	 * @return
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@HasPermission("codegen_field_type_view")
	public R getFieldTypePage(Page page, GenFieldType fieldType) {
		return R.ok(fieldTypeService.page(page,
				Wrappers.<GenFieldType>lambdaQuery()
					.like(StrUtil.isNotBlank(fieldType.getColumnType()), GenFieldType::getColumnType,
							fieldType.getColumnType())));
	}

	@Operation(summary = "查询列表", description = "查询列表")
	@GetMapping("/list")
	@HasPermission("codegen_field_type_view")
	public R list(GenFieldType fieldType) {
		return R.ok(fieldTypeService.list(Wrappers.query(fieldType)));
	}

	/**
	 * 通过id查询列属性
	 * @param id id
	 * @return R
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/details/{id}")
	@HasPermission("codegen_field_type_view")
	public R getById(@PathVariable("id") Long id) {
		return R.ok(fieldTypeService.getById(id));
	}

	@GetMapping("/details")
	@HasPermission("codegen_field_type_view")
	public R getDetails(GenFieldType query) {
		return R.ok(fieldTypeService.getOne(Wrappers.query(query), false));
	}

	/**
	 * 新增列属性
	 * @param fieldType 列属性
	 * @return R
	 */
	@Operation(summary = "新增列属性", description = "新增列属性")
	@SysLog("新增列属性")
	@PostMapping
	@HasPermission("codegen_field_type_add")
	public R save(@RequestBody GenFieldType fieldType) {
		return R.ok(fieldTypeService.save(fieldType));
	}

	/**
	 * 修改列属性
	 * @param fieldType 列属性
	 * @return R
	 */
	@Operation(summary = "修改列属性", description = "修改列属性")
	@SysLog("修改列属性")
	@PutMapping
	@HasPermission("codegen_field_type_edit")
	public R updateById(@RequestBody GenFieldType fieldType) {
		return R.ok(fieldTypeService.updateById(fieldType));
	}

	/**
	 * 通过id删除列属性
	 * @param ids id
	 * @return R
	 */
	@Operation(summary = "通过id删除列属性", description = "通过id删除列属性")
	@SysLog("通过id删除列属性")
	@DeleteMapping
	@HasPermission("codegen_field_type_del")
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(fieldTypeService.removeBatchByIds(CollUtil.toList(ids)));
	}

	/**
	 * 导出excel 表格
	 * @param fieldType 查询条件
	 * @return excel 文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@HasPermission("codegen_field_type_export")
	public List<GenFieldType> export(GenFieldType fieldType) {
		return fieldTypeService.list(Wrappers.query(fieldType));
	}

}
