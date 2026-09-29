/*
 *    Copyright (c) 2018-2025, lengleng All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * Neither the name of the pig4cloud.com developer nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * Author: lengleng (wangiegie@gmail.com)
 */

package com.lotus.bixi.generator.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.NamingCase;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.lotus.bixi.generator.config.BixiGeneratorDefaultProperties;
import com.lotus.bixi.generator.entity.GenTable;
import com.lotus.bixi.generator.entity.GenTableColumn;
import com.lotus.bixi.generator.entity.GenTemplate;
import com.lotus.bixi.generator.service.*;
import com.lotus.bixi.generator.service.output.AtomicGeneratorWriter;
import com.lotus.bixi.generator.service.output.GeneratedArtifact;
import com.lotus.bixi.generator.service.output.GeneratorOutputPolicy;
import com.lotus.bixi.generator.template.BuiltInTemplateCatalog;
import com.lotus.bixi.generator.util.CommonColumnFiledEnum;
import com.lotus.bixi.generator.util.VelocityKit;
import com.lotus.bixi.generator.util.vo.GroupVO;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author 唐磊
 * @date 2025-01-01
 * <p>
 * 代码生成器
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "generator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GeneratorServiceImpl implements GeneratorService {
	private static final Set<String> FORBIDDEN_DELIVERY_TOKENS = Set.of(
		"password", "passwd", "pwd", "secret", "token", "key", "credential", "credentials", "salt");

	private final BixiGeneratorDefaultProperties configurationProperties;

	private final GenTableColumnService columnService;

	private final GenFieldTypeService fieldTypeService;

	private final GenTableService tableService;

	private final GenGroupService genGroupService;

	private final BuiltInTemplateCatalog builtInTemplateCatalog;

	/**
	 * 生成代码zip写出
	 * @param tableId 表
	 * @param zip 输出流
	 */
	@Override
	@SneakyThrows
	public void downloadCode(Long tableId, String templateVersion, ZipOutputStream zip) {
		RenderedBundle bundle = renderBundle(tableId);
		requirePreviewVersion(templateVersion, bundle);
		GeneratorOutputPolicy policy = outputPolicy();
		for (GeneratedArtifact artifact : bundle.artifacts()) {
			Path normalized = projectRoot().relativize(policy.resolve(artifact.relativePath()));
			zip.putNextEntry(new ZipEntry(normalized.toString().replace('\\', '/')));
			zip.write(artifact.content());
			zip.flush();
			zip.closeEntry();
		}
	}

	/**
	 * 表达式优化的预览代码方法
	 * @param tableId 表
	 * @return [{模板名称:渲染结果}]
	 */
	@Override
	@SneakyThrows
	public List<Map<String, String>> preview(Long tableId) {
		RenderedBundle bundle = renderBundle(tableId);
		return bundle.artifacts().stream().map(artifact -> Map.of(
			"code", new String(artifact.content(), StandardCharsets.UTF_8),
			"codePath", artifact.relativePath(),
			"templateVersion", bundle.templateVersion()
		)).toList();
	}

	/**
	 * 目标目录写入渲染结果方法
	 * @param tableIds 表
	 */
	@Override
	public List<Path> generatorCode(List<Long> tableIds, String templateVersion, boolean overwrite) {
		if (tableIds == null || tableIds.isEmpty()) {
			throw new IllegalArgumentException("至少选择一张生成表");
		}
		if (tableIds.stream().distinct().count() != 1) {
			throw new IllegalArgumentException("批量生成必须逐表预览并提交各自的模板版本");
		}
		List<GeneratedArtifact> artifacts = tableIds.stream().distinct().map(this::renderBundle)
			.peek(bundle -> requirePreviewVersion(templateVersion, bundle))
			.flatMap(bundle -> bundle.artifacts().stream())
			.toList();
		return new AtomicGeneratorWriter(outputPolicy(), projectRoot()).write(artifacts, overwrite);
	}

	private RenderedBundle renderBundle(Long tableId) {
		Map<String, Object> dataModel = getDataModel(tableId);
		Long style = (Long) dataModel.get("style");
		List<GenTemplate> templates = templatesFor(style, Boolean.TRUE.equals(dataModel.get("isParentChild")));
		dataModel.put("backendPath", configurationProperties.getBackendPath());
		dataModel.put("apiPath", configurationProperties.getApiPath());
		dataModel.put("frontendPath", configurationProperties.getFrontendPath());
		List<GeneratedArtifact> artifacts = templates.stream().map(template -> GeneratedArtifact.utf8(
			VelocityKit.renderStr(template.getGeneratorPath(), dataModel),
			VelocityKit.renderStr(template.getTemplateCode(), dataModel)
		)).toList();
		return new RenderedBundle(GeneratorTemplateSnapshot.version(tableId, style, templates, artifacts), artifacts);
	}

	private List<GenTemplate> templatesFor(Long style, boolean parentChild) {
		if (Long.valueOf(BuiltInTemplateCatalog.DEFAULT_GROUP_ID).equals(style)) {
			BuiltInTemplateCatalog.Snapshot snapshot = builtInTemplateCatalog.snapshot();
			return GeneratorTemplateSnapshot.ordered(parentChild
				? snapshot.parentChildTemplates() : snapshot.templates());
		}
		GroupVO group = genGroupService.getGroupVoById(style);
		if (group == null || CollUtil.isEmpty(group.getTemplateList())) {
			throw new IllegalStateException("所选代码风格没有可用模板");
		}
		return GeneratorTemplateSnapshot.ordered(group.getTemplateList());
	}

	private Path projectRoot() {
		try {
			return Path.of(configurationProperties.getProjectRoot()).toAbsolutePath().normalize().toRealPath();
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("项目根目录不存在", ex);
		}
	}

	private GeneratorOutputPolicy outputPolicy() {
		return new GeneratorOutputPolicy(projectRoot(), configurationProperties.getAllowedOutputRoots().stream()
			.map(Path::of).toList());
	}

	private record RenderedBundle(String templateVersion, List<GeneratedArtifact> artifacts) { }

	private static void requirePreviewVersion(String templateVersion, RenderedBundle bundle) {
		if (StrUtil.isBlank(templateVersion)) {
			throw new IllegalArgumentException("模板版本不能为空，请先预览");
		}
		if (!templateVersion.equals(bundle.templateVersion())) {
			throw new IllegalStateException("模板已变化，请重新预览后再生成");
		}
	}

	/**
	 * 通过 Lambda 表达式优化的获取数据模型方法
	 * @param tableId 表格 ID
	 * @return 数据模型 Map 对象
	 */
	private Map<String, Object> getDataModel(Long tableId) {
		// 获取表格信息
		GenTable table = tableService.getById(tableId);
		if (table == null) {
			throw new IllegalArgumentException("生成表不存在: " + tableId);
		}
		boolean parentChild = validateRelationshipMetadata(table);
		// 获取字段列表
		List<GenTableColumn> fieldList = columnService.lambdaQuery()
			.eq(GenTableColumn::getDsName, table.getDsName())
			.eq(GenTableColumn::getTableName, table.getTableName())
			.orderByAsc(GenTableColumn::getSn)
			.list();

		table.setFieldList(fieldList);

		// 创建数据模型对象
		Map<String, Object> dataModel = new HashMap<>();

		// 填充数据模型
		dataModel.put("opensource", true);
		dataModel.put("isSpringBoot3", isSpringBoot3());
		dataModel.put("dbType", table.getDbType());
		dataModel.put("package", table.getPackageName());
		dataModel.put("packagePath", table.getPackageName().replace(".", "/"));
		dataModel.put("version", table.getVersion());
		dataModel.put("moduleName", table.getModuleName());
		dataModel.put("ModuleName", StrUtil.upperFirst(table.getModuleName()));
		dataModel.put("functionName", table.getFunctionName());
		dataModel.put("FunctionName", StrUtil.upperFirst(table.getFunctionName()));
		dataModel.put("permissionPrefix", NamingCase.toUnderlineCase(table.getModuleName()) + "_"
			+ NamingCase.toUnderlineCase(table.getFunctionName()));
		dataModel.put("formLayout", table.getFormLayout());
		Long style = table.getStyle() == null ? BuiltInTemplateCatalog.DEFAULT_GROUP_ID : table.getStyle();
		dataModel.put("style", style);
		dataModel.put("author", table.getAuthor());
		dataModel.put("datetime", DateUtil.now());
		dataModel.put("date", DateUtil.today());
		setFieldTypeList(dataModel, table);

		// 获取导入的包列表
		Set<String> importList = fieldTypeService.getPackageByTableId(table.getDsName(), table.getTableName());
		dataModel.put("importList", importList);
		dataModel.put("tableName", table.getTableName());
		dataModel.put("tableComment", table.getTableComment());
		dataModel.put("className", StrUtil.lowerFirst(table.getClassName()));
		dataModel.put("ClassName", table.getClassName());
		dataModel.put("fieldList", table.getFieldList());
		setDeliveryFieldLists(dataModel, table.getFieldList());
		dataModel.put("isParentChild", parentChild);
		boolean tenant = table.getFieldList().stream()
			.anyMatch(column -> "tenant_id".equalsIgnoreCase(column.getFieldName()));
		dataModel.put("isTenant", tenant);

		// 设置子表
		if (parentChild) {
			String childTableName = table.getChildTableName().trim();
			String childClassName = NamingCase.toPascalCase(childTableName);
			if (Objects.equals(table.getClassName(), childClassName)) {
				throw new IllegalArgumentException("父子表生成类名冲突: " + childClassName);
			}
			List<GenTableColumn> childFieldList = columnService.lambdaQuery()
				.eq(GenTableColumn::getDsName, table.getDsName())
				.eq(GenTableColumn::getTableName, childTableName)
				.orderByAsc(GenTableColumn::getSn)
				.list();
			if (CollUtil.isEmpty(childFieldList)) {
				throw new IllegalArgumentException("子表字段不存在: " + childTableName);
			}
			GenTableColumn mainRelation = requireRelationField(fieldList, table.getMainField(), "主表关联键");
			GenTableColumn childRelation = requireRelationField(childFieldList, table.getChildField(), "子表关联键");
			if (!BooleanUtil.toBoolean(mainRelation.getPrimaryPk())) {
				throw new IllegalArgumentException("主表关联键必须是主键");
			}
			if (!"id".equalsIgnoreCase(mainRelation.getFieldName())
					|| !"Long".equals(mainRelation.getAttrType())) {
				throw new IllegalArgumentException("主表关联键必须是 Long 类型的 id 主键");
			}
			if (BooleanUtil.toBoolean(childRelation.getPrimaryPk())) {
				throw new IllegalArgumentException("子表关联键不能是主键");
			}
			if (!"Long".equals(childRelation.getAttrType())) {
				throw new IllegalArgumentException("子表关联键必须是 Long 类型");
			}
			if (isManagedRelationField(childRelation.getFieldName())) {
				throw new IllegalArgumentException("子表关联键不能使用基础字段: " + childRelation.getFieldName());
			}
			List<GenTableColumn> childFormList = childFieldList.stream()
				.filter(column -> BooleanUtil.toBoolean(column.getFormItem()))
				.filter(column -> !BooleanUtil.toBoolean(column.getPrimaryPk()))
				.filter(column -> !childRelation.getFieldName().equalsIgnoreCase(column.getFieldName()))
				.toList();
			dataModel.put("childFieldList", childFieldList);
			dataModel.put("childFormList", childFormList);
			dataModel.put("childTableName", childTableName);
			dataModel.put("mainField", mainRelation.getAttrName());
			dataModel.put("childField", childRelation.getAttrName());
			dataModel.put("childRelationType", childRelation.getAttrType());
			dataModel.put("ChildClassName", childClassName);
			dataModel.put("childClassName", StrUtil.lowerFirst(childClassName));
			dataModel.put("childPermissionPrefix", NamingCase.toUnderlineCase(table.getModuleName()) + "_"
				+ NamingCase.toUnderlineCase(NamingCase.toPascalCase(childTableName)));
			dataModel.put("childImportList",
				fieldTypeService.getPackageByTableId(table.getDsName(), childTableName));
			dataModel.put("hasRequiredChildFields", childFormList.stream()
				.anyMatch(column -> BooleanUtil.toBoolean(column.getFormRequired())));
			dataModel.put("isChildTenant", childFieldList.stream()
				.anyMatch(column -> "tenant_id".equalsIgnoreCase(column.getFieldName())));
		}

		return dataModel;
	}

	private static boolean validateRelationshipMetadata(GenTable table) {
		boolean hasChildTable = StrUtil.isNotBlank(table.getChildTableName());
		boolean hasMainField = StrUtil.isNotBlank(table.getMainField());
		boolean hasChildField = StrUtil.isNotBlank(table.getChildField());
		int configured = (hasChildTable ? 1 : 0) + (hasMainField ? 1 : 0) + (hasChildField ? 1 : 0);
		if (configured != 0 && configured != 3) {
			throw new IllegalArgumentException("父子表关系元数据必须同时配置: 子表、主表关联键、子表关联键");
		}
		if (configured == 3 && table.getTableName().equalsIgnoreCase(table.getChildTableName().trim())) {
			throw new IllegalArgumentException("子表不能与主表相同");
		}
		return configured == 3;
	}

	private static GenTableColumn requireRelationField(List<GenTableColumn> fields, String configuredName,
			String label) {
		String name = configuredName.trim();
		List<GenTableColumn> matches = fields.stream()
			.filter(field -> name.equalsIgnoreCase(field.getFieldName()))
			.toList();
		if (matches.isEmpty()) throw new IllegalArgumentException(label + "不存在: " + name);
		if (matches.size() > 1) throw new IllegalArgumentException(label + "重复: " + name);
		return matches.get(0);
	}

	private static boolean isManagedRelationField(String fieldName) {
		return "id".equalsIgnoreCase(fieldName) || java.util.Arrays.stream(CommonColumnFiledEnum.values())
			.anyMatch(field -> field.name().equalsIgnoreCase(fieldName));
	}

	/**
	 * 判断当前是否是 SpringBoot3 版本
	 * @return true/fasle
	 */
	private boolean isSpringBoot3() {
		return StrUtil.startWith(SpringBootVersion.getVersion(), "3");
	}

	/**
	 * 将表字段按照类型分组并存储到数据模型中
	 * @param dataModel 存储数据的 Map 对象
	 * @param table 表信息对象
	 */
	private void setFieldTypeList(Map<String, Object> dataModel, GenTable table) {
		// 按字段类型分组，使用 Map 存储不同类型的字段列表
		Map<Boolean, List<GenTableColumn>> typeMap = table.getFieldList()
			.stream()
			.collect(Collectors.partitioningBy(column -> BooleanUtil.toBoolean(column.getPrimaryPk())));

		// 从分组后的 Map 中获取不同类型的字段列表
		List<GenTableColumn> primaryList = typeMap.get(true);
		List<GenTableColumn> formList = typeMap.get(false)
			.stream()
			.filter(column -> BooleanUtil.toBoolean(column.getFormItem()))
			.collect(Collectors.toList());
		List<GenTableColumn> gridList = typeMap.get(false)
			.stream()
			.filter(column -> BooleanUtil.toBoolean(column.getGridItem()))
			.collect(Collectors.toList());
		List<GenTableColumn> queryList = typeMap.get(false)
			.stream()
			.filter(column -> BooleanUtil.toBoolean(column.getQueryItem()))
			.collect(Collectors.toList());
		boolean hasRequiredFields = formList.stream()
			.anyMatch(column -> BooleanUtil.toBoolean(column.getFormRequired()));
		boolean hasRequiredStringFields = formList.stream()
			.anyMatch(column -> BooleanUtil.toBoolean(column.getFormRequired())
				&& "String".equals(column.getAttrType()));

		if (CollUtil.isNotEmpty(primaryList)) {
			dataModel.put("pk", primaryList.get(0));
		}
		dataModel.put("primaryList", primaryList);
		dataModel.put("formList", formList);
		dataModel.put("gridList", gridList);
		dataModel.put("queryList", queryList);
		dataModel.put("hasRequiredFields", hasRequiredFields);
		dataModel.put("hasRequiredStringFields", hasRequiredStringFields);
	}

	private static void setDeliveryFieldLists(Map<String, Object> dataModel, List<GenTableColumn> fields) {
		List<GenTableColumn> importFields = fields.stream()
			.filter(column -> BooleanUtil.toBoolean(column.getFormItem()))
			.filter(column -> !isManagedDeliveryField(column.getFieldName()))
			.filter(column -> !isForbiddenSensitiveField(column))
			.toList();
		List<GenTableColumn> exportFields = fields.stream()
			.filter(column -> BooleanUtil.toBoolean(column.getGridItem()))
			.filter(column -> !isManagedDeliveryField(column.getFieldName()))
			.filter(column -> !isForbiddenSensitiveField(column))
			.toList();
		dataModel.put("importFieldList", importFields);
		dataModel.put("exportFieldList", exportFields);
		dataModel.put("hasRequiredImportFields", importFields.stream()
			.anyMatch(column -> BooleanUtil.toBoolean(column.getFormRequired())));
		dataModel.put("emailExportFields", exportFields.stream()
			.filter(column -> "String".equals(column.getAttrType()))
			.filter(column -> {
				Set<String> tokens = deliveryTokens(column);
				return tokens.contains("email") || tokens.contains("mail");
			})
			.map(GenTableColumn::getAttrName).collect(Collectors.toSet()));
		dataModel.put("phoneExportFields", exportFields.stream()
			.filter(column -> "String".equals(column.getAttrType()))
			.filter(column -> {
				Set<String> tokens = deliveryTokens(column);
				return tokens.contains("phone") || tokens.contains("mobile") || tokens.contains("telephone");
			})
			.map(GenTableColumn::getAttrName).collect(Collectors.toSet()));
		dataModel.put("identityExportFields", exportFields.stream()
			.filter(column -> "String".equals(column.getAttrType()))
			.filter(column -> {
				Set<String> tokens = deliveryTokens(column);
				return tokens.contains("idcard") || tokens.contains("identity")
					|| tokens.contains("identitycard") || tokens.contains("certno")
					|| tokens.contains("certificate")
					|| (tokens.contains("id") && tokens.contains("card"))
					|| (tokens.contains("cert") && tokens.contains("no"));
			})
			.map(GenTableColumn::getAttrName).collect(Collectors.toSet()));
	}

	private static boolean isManagedDeliveryField(String fieldName) {
		return "id".equalsIgnoreCase(fieldName) || "status".equalsIgnoreCase(fieldName)
			|| "data_status".equalsIgnoreCase(fieldName) || "remark".equalsIgnoreCase(fieldName)
			|| java.util.Arrays.stream(CommonColumnFiledEnum.values())
				.anyMatch(field -> field.name().equalsIgnoreCase(fieldName));
	}

	private static boolean isForbiddenSensitiveField(GenTableColumn column) {
		return deliveryTokens(column).stream().anyMatch(FORBIDDEN_DELIVERY_TOKENS::contains);
	}

	private static Set<String> deliveryTokens(GenTableColumn column) {
		return java.util.stream.Stream.of(column.getFieldName(), column.getAttrName())
			.map(name -> Objects.toString(name, ""))
			.flatMap(name -> java.util.Arrays.stream(name.split(
				"(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[^A-Za-z0-9]+")))
			.map(token -> token.toLowerCase(java.util.Locale.ROOT))
			.filter(token -> !token.isBlank())
			.collect(Collectors.toSet());
	}

}
