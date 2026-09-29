package com.lotus.bixi.generator.service.impl;

import cn.hutool.core.text.NamingCase;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.generator.entity.GenFieldType;
import com.lotus.bixi.generator.entity.GenTableColumn;
import com.lotus.bixi.generator.mapper.GenFieldTypeMapper;
import com.lotus.bixi.generator.mapper.GenTableColumnMapper;
import com.lotus.bixi.generator.service.GenTableColumnService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 表字段信息管理
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "generator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GenTableColumnServiceImpl extends ServiceImpl<GenTableColumnMapper, GenTableColumn>
		implements GenTableColumnService {

	private final GenFieldTypeMapper fieldTypeMapper;

	/**
	 * 初始化表单字段列表，主要是将数据库表中的字段转化为表单需要的字段数据格式，并为审计字段排序
	 * @param tableFieldList 表单字段列表
	 */
	@Override
	public void initFieldList(List<GenTableColumn> tableFieldList) {
		// 字段类型、属性类型映射
		List<GenFieldType> list = fieldTypeMapper.selectList(Wrappers.emptyWrapper());
		Map<String, GenFieldType> fieldTypeMap = new LinkedHashMap<>(list.size());
		list.forEach(
				fieldTypeMapping -> fieldTypeMap.put(fieldTypeMapping.getColumnType().toLowerCase(), fieldTypeMapping));

		// 索引计数器
		AtomicInteger index = new AtomicInteger(0);
		tableFieldList.forEach(field -> {
			// 将字段名转化为驼峰格式
			field.setAttrName(NamingCase.toCamelCase(field.getFieldName()));

			// 获取字段对应的类型
			GenFieldType fieldTypeMapping = fieldTypeMap.getOrDefault(field.getFieldType().toLowerCase(), null);
			if (fieldTypeMapping == null) {
				// 没找到对应的类型，则为Object类型
				field.setAttrType("Object");
			}
			else {
				field.setAttrType(fieldTypeMapping.getAttrType());
				field.setPackageName(fieldTypeMapping.getPackageName());
			}

			// 设置查询类型和表单查询类型都为“=”
			field.setQueryType("=");
			field.setQueryFormType("text");

			// 设置表单类型为文本框类型
			field.setFormType("text");

			// 保证审计字段最后显示
			field.setSn(Objects.isNull(field.getSn()) ? index.getAndIncrement() : field.getSn());
		});
	}

	@Override
	public boolean updatePhysicalMetadataById(GenTableColumn column) {
		if (column == null || column.getId() == null) {
			throw new IllegalArgumentException("字段ID不能为空");
		}
		return this.lambdaUpdate()
			.eq(GenTableColumn::getId, column.getId())
			.set(GenTableColumn::getFieldName, column.getFieldName())
			.set(GenTableColumn::getFieldType, column.getFieldType())
			.set(GenTableColumn::getFieldComment, column.getFieldComment())
			.set(GenTableColumn::getPrimaryPk, column.getPrimaryPk())
			.set(GenTableColumn::getAttrName, column.getAttrName())
			.set(GenTableColumn::getAttrType, column.getAttrType())
			.set(GenTableColumn::getPackageName, column.getPackageName())
			.update();
	}

	/**
	 * 更新指定数据源和表名的表单字段信息
	 * @param dsName 数据源名称
	 * @param tableName 表名
	 * @param tableFieldList 表单字段列表
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateTableField(String dsName, String tableName, List<GenTableColumn> tableFieldList) {
		if (dsName == null || dsName.isBlank() || tableName == null || tableName.isBlank()) {
			throw new IllegalArgumentException("数据源和表名不能为空");
		}
		if (tableFieldList == null) throw new IllegalArgumentException("字段配置不能为空");

		Map<Long, GenTableColumn> persistedById = this.list(Wrappers.<GenTableColumn>lambdaQuery()
			.eq(GenTableColumn::getDsName, dsName)
			.eq(GenTableColumn::getTableName, tableName))
			.stream()
			.collect(Collectors.toMap(GenTableColumn::getId, field -> field, (left, right) -> left,
				LinkedHashMap::new));
		AtomicInteger sort = new AtomicInteger();
		Set<Long> submittedIds = new HashSet<>();
		List<GenTableColumn> updates = tableFieldList.stream().map(submitted -> {
			if (submitted == null || submitted.getId() == null
					|| !submittedIds.add(submitted.getId()) || !persistedById.containsKey(submitted.getId())) {
				throw new IllegalArgumentException("字段不属于当前表或字段ID重复");
			}
			GenTableColumn trusted = persistedById.get(submitted.getId());
			trusted.setSn(sort.getAndIncrement());
			trusted.setFieldComment(submitted.getFieldComment());
			trusted.setFormItem(submitted.getFormItem());
			trusted.setFormRequired(submitted.getFormRequired());
			trusted.setFormType(submitted.getFormType());
			trusted.setFormValidator(submitted.getFormValidator());
			trusted.setGridItem(submitted.getGridItem());
			trusted.setGridSort(submitted.getGridSort());
			trusted.setQueryItem(submitted.getQueryItem());
			trusted.setQueryType(submitted.getQueryType());
			trusted.setQueryFormType(submitted.getQueryFormType());
			trusted.setFieldDict(submitted.getFieldDict());
			return trusted;
		}).toList();
		if (!updates.isEmpty() && !this.updateBatchById(updates)) {
			throw new IllegalStateException("更新字段配置失败");
		}
	}

}
