package com.lotus.bixi.generator.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.NamingCase;
import cn.hutool.core.util.StrUtil;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.generator.config.BixiGeneratorDefaultProperties;
import com.lotus.bixi.generator.dto.GenTableImportResult;
import com.lotus.bixi.generator.entity.GenTable;
import com.lotus.bixi.generator.entity.GenTableColumn;
import com.lotus.bixi.generator.mapper.GenTableMapper;
import com.lotus.bixi.generator.service.GenGroupService;
import com.lotus.bixi.generator.service.GenTableColumnService;
import com.lotus.bixi.generator.service.GenTableService;
import com.lotus.bixi.generator.template.BuiltInTemplateCatalog;
import com.lotus.bixi.generator.util.AutoFillEnum;
import com.lotus.bixi.generator.util.BoolFillEnum;
import com.lotus.bixi.generator.util.CommonColumnFiledEnum;
import com.lotus.bixi.generator.util.GenKit;
import lombok.RequiredArgsConstructor;
import org.anyline.metadata.Column;
import org.anyline.metadata.Database;
import org.anyline.metadata.Table;
import org.anyline.proxy.CacheProxy;
import org.anyline.proxy.ServiceProxy;
import org.anyline.service.AnylineService;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 列属性
 *
 * @author 唐磊x code generator
 * @date 2025-01-01
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "generator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GenTableServiceImpl extends ServiceImpl<GenTableMapper, GenTable> implements GenTableService {

	private static final Pattern OWNERSHIP_MARKER = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

	private final BixiGeneratorDefaultProperties configurationProperties;

	private final GenTableColumnService columnService;

	private final GenGroupService genGroupService;

	/**
	 * 查询表ddl 语句
	 * @param dsName 数据源名称
	 * @param tableName 表名称
	 * @return ddl 语句
	 * @throws Exception
	 */
	@Override
	public String queryTableDdl(String dsName, String tableName) throws Exception {
		// 手动切换数据源
		DynamicDataSourceContextHolder.push(dsName);
		try {
			Table table = ServiceProxy.metadata().table(tableName); // 获取表结构
			table.execute(false);// 不执行SQL
			ServiceProxy.ddl().create(table);
			return table.getDdl();// 返回创建表的DDL
		}
		finally {
			DynamicDataSourceContextHolder.poll();
		}
	}

	/**
	 * 查询表的全部字段
	 * @param dsName 数据源
	 * @param tableName 表名称
	 * @return column
	 */
	@Override
	public List<String> queryTableColumn(String dsName, String tableName) {
		// 手动切换数据源
		DynamicDataSourceContextHolder.push(dsName);
		try {
			CacheProxy.clear();
			return ServiceProxy.metadata().columns(tableName).values().stream().map(Column::getName).toList();
		}
		finally {
			DynamicDataSourceContextHolder.poll();
		}
	}

	/**
	 * 查询对应数据源的表
	 * @param page 分页信息
	 * @param table 查询条件
	 * @return 表
	 */
	@Override
	public IPage queryTablePage(Page<Table> page, GenTable table) {
		// 手动切换数据源
		DynamicDataSourceContextHolder.push(table.getDsName());
		try {
			CacheProxy.clear();
			List<Table> tableList = ServiceProxy.metadata().tables().values().stream().filter(t -> {
				if (StrUtil.isBlank(table.getTableName())) {
					return true;
				}
				return StrUtil.containsIgnoreCase(t.getName(false), table.getTableName());
			}).toList();

			// 根据 page 进行分页
			List<Table> records = CollUtil.page((int) page.getCurrent() - 1, (int) page.getSize(), tableList);
			page.setTotal(tableList.size());
			page.setRecords(records);
			return page;
		}
		finally {
			DynamicDataSourceContextHolder.poll();
		}
	}

	/**
	 * 查询数据源里面的全部表
	 * @param dsName 数据源名称
	 * @return table
	 */
	@Override
	public List<String> queryTableList(String dsName) {
		// 手动切换数据源
		DynamicDataSourceContextHolder.push(dsName);
		try {
			CacheProxy.clear();
			return ServiceProxy.metadata().tables().values().stream().map(Table::getName).toList();
		}
		finally {
			DynamicDataSourceContextHolder.poll();
		}
	}

	/**
	 * 查询表信息（列），然后插入到中间表中
	 * @param dsName 数据源
	 * @param tableName 表名
	 * @return GenTable
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public GenTable queryOrBuildTable(String dsName, String tableName) {
		GenTable genTable = findConfiguredTable(dsName, tableName);
		// 如果 genTable 为空， 执行导入
		if (Objects.isNull(genTable)) {
			genTable = this.tableImport(dsName, tableName);
		}

		return loadConfigurationDetails(genTable, dsName, tableName);
	}

	@Override
	public GenTable findConfiguredTable(String dsName, String tableName) {
		validateTableIdentity(dsName, tableName);
		return baseMapper.selectOne(Wrappers.<GenTable>lambdaQuery()
			.eq(GenTable::getTableName, tableName)
			.eq(GenTable::getDsName, dsName));
	}

	@Override
	public GenTable findConfiguredTableDetails(String dsName, String tableName) {
		GenTable configured = findConfiguredTable(dsName, tableName);
		return configured == null ? null : loadConfigurationDetails(configured, dsName, tableName);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public GenTableImportResult importTable(String dsName, String tableName, String author) {
		validateTableIdentity(dsName, tableName);
		if (author == null || !OWNERSHIP_MARKER.matcher(author).matches()) {
			throw new IllegalArgumentException("生成表归属标记无效");
		}
		GenTable existing = findConfiguredTable(dsName, tableName);
		if (existing != null) {
			return new GenTableImportResult(false, loadConfigurationDetails(existing, dsName, tableName));
		}
		try {
			GenTable imported = tableImport(dsName, tableName, author);
			imported.setGroupList(genGroupService.list());
			return new GenTableImportResult(true, imported);
		}
		catch (DuplicateKeyException collision) {
			throw new IllegalStateException("生成表配置并发导入冲突: " + dsName + "." + tableName, collision);
		}
	}

	private GenTable loadConfigurationDetails(GenTable table, String dsName, String tableName) {
		List<GenTableColumn> fieldList = columnService.list(Wrappers.<GenTableColumn>lambdaQuery()
			.eq(GenTableColumn::getDsName, dsName)
			.eq(GenTableColumn::getTableName, tableName)
			.orderByAsc(GenTableColumn::getSn));
		table.setFieldList(fieldList);
		table.setGroupList(genGroupService.list());
		return table;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public GenTable syncTable(String dsName, String tableName) {
		GenTable configured = findConfiguredTableForUpdate(dsName, tableName);
		if (configured == null) {
			throw new IllegalArgumentException("生成表配置不存在，请先导入: " + dsName + "." + tableName);
		}
		List<GenTableColumn> persistedColumns = columnService.list(Wrappers.<GenTableColumn>lambdaQuery()
			.eq(GenTableColumn::getDsName, dsName)
			.eq(GenTableColumn::getTableName, tableName));

		TableMetadataSnapshot metadata = loadTableMetadata(dsName, tableName);
		configured.setTableComment(metadata.tableComment());
		configured.setDbType(metadata.dbType());
		updatePhysicalTableMetadata(configured);

		List<GenTableColumn> physicalColumns = new ArrayList<>(metadata.columns());
		columnService.initFieldList(physicalColumns);
		ColumnReconciliation reconciliation = reconcileColumns(persistedColumns, physicalColumns, dsName, tableName);
		for (GenTableColumn column : reconciliation.updates()) {
			if (!columnService.updatePhysicalMetadataById(column)) {
				throw new IllegalStateException("生成表字段更新失败: " + dsName + "." + tableName);
			}
		}
		if (!reconciliation.droppedIds().isEmpty()
				&& !columnService.removeByIds(reconciliation.droppedIds())) {
			throw new IllegalStateException("生成表字段删除失败: " + dsName + "." + tableName);
		}
		if (!reconciliation.inserts().isEmpty()
				&& !columnService.saveBatch(reconciliation.inserts())) {
			throw new IllegalStateException("生成表字段新增失败: " + dsName + "." + tableName);
		}

		configured.setFieldList(reconciliation.currentColumns());
		configured.setGroupList(genGroupService.list());
		return configured;
	}

	private GenTable findConfiguredTableForUpdate(String dsName, String tableName) {
		validateTableIdentity(dsName, tableName);
		return baseMapper.selectOne(Wrappers.<GenTable>lambdaQuery()
			.eq(GenTable::getTableName, tableName)
			.eq(GenTable::getDsName, dsName)
			.last("FOR UPDATE"));
	}

	private static ColumnReconciliation reconcileColumns(List<GenTableColumn> persistedColumns,
			List<GenTableColumn> physicalColumns, String dsName, String tableName) {
		Map<String, GenTableColumn> persistedByName = new LinkedHashMap<>();
		for (GenTableColumn persisted : persistedColumns) {
			String normalized = normalizedFieldName(persisted.getFieldName(), dsName, tableName);
			if (persistedByName.putIfAbsent(normalized, persisted) != null) {
				throw new IllegalStateException("生成表存在重复字段配置: " + dsName + "." + tableName + "." + normalized);
			}
		}

		Set<String> physicalNames = new HashSet<>();
		List<GenTableColumn> updates = new ArrayList<>();
		List<GenTableColumn> inserts = new ArrayList<>();
		List<GenTableColumn> current = new ArrayList<>(physicalColumns.size());
		for (GenTableColumn physical : physicalColumns) {
			String normalized = normalizedFieldName(physical.getFieldName(), dsName, tableName);
			if (!physicalNames.add(normalized)) {
				throw new IllegalStateException("物理表存在重复字段: " + dsName + "." + tableName + "." + normalized);
			}
			GenTableColumn persisted = persistedByName.remove(normalized);
			if (persisted == null) {
				inserts.add(physical);
				current.add(physical);
				continue;
			}
			copyPhysicalColumnMetadata(physical, persisted);
			updates.add(persisted);
			current.add(persisted);
		}

		List<Long> droppedIds = persistedByName.values().stream().map(column -> {
			if (column.getId() == null) {
				throw new IllegalStateException("待删除字段缺少ID: " + dsName + "." + tableName + "." + column.getFieldName());
			}
			return column.getId();
		}).toList();
		return new ColumnReconciliation(updates, inserts, droppedIds, current);
	}

	private static String normalizedFieldName(String fieldName, String dsName, String tableName) {
		if (StrUtil.isBlank(fieldName)) {
			throw new IllegalStateException("字段名称不能为空: " + dsName + "." + tableName);
		}
		return fieldName.trim().toLowerCase(Locale.ROOT);
	}

	private static void copyPhysicalColumnMetadata(GenTableColumn source, GenTableColumn target) {
		target.setFieldName(source.getFieldName());
		target.setFieldType(source.getFieldType());
		target.setFieldComment(source.getFieldComment());
		target.setPrimaryPk(source.getPrimaryPk());
		target.setAttrName(source.getAttrName());
		target.setAttrType(source.getAttrType());
		target.setPackageName(source.getPackageName());
	}

	protected void updatePhysicalTableMetadata(GenTable table) {
		boolean updated = this.lambdaUpdate()
			.eq(GenTable::getId, table.getId())
			.set(GenTable::getTableComment, table.getTableComment())
			.set(GenTable::getDbType, table.getDbType())
			.update();
		if (!updated) throw new IllegalStateException("生成表配置更新失败: " + table.getDsName() + "." + table.getTableName());
	}

	protected GenTable tableImport(String dsName, String tableName) {
		return tableImport(dsName, tableName, configurationProperties.getAuthor());
	}

	protected GenTable tableImport(String dsName, String tableName, String author) {
		TableMetadataSnapshot metadata = loadTableMetadata(dsName, tableName);
		GenTable table = new GenTable();
		// 获取默认表配置信息 （）

		table.setPackageName(configurationProperties.getPackageName());
		table.setVersion(configurationProperties.getVersion());
		table.setBackendPath(configurationProperties.getBackendPath());
		table.setFrontendPath(configurationProperties.getFrontendPath());
		table.setAuthor(author);
		table.setEmail(configurationProperties.getEmail());
		table.setTableName(tableName);
		table.setDsName(dsName);
		table.setTableComment(metadata.tableComment());

		table.setDbType(metadata.dbType());
		table.setFormLayout(configurationProperties.getFormLayout());
		table.setGeneratorType(configurationProperties.getGeneratorType());
		table.setStyle(BuiltInTemplateCatalog.DEFAULT_GROUP_ID);
		table.setClassName(NamingCase.toPascalCase(tableName));
		// 模块名称默认为 admin
		table.setModuleName(configurationProperties.getModuleName());
		table.setFunctionName(GenKit.getFunctionName(tableName));
		table.setCreateTime(LocalDateTime.now());

		if (!this.save(table)) {
			throw new IllegalStateException("生成表配置导入失败: " + dsName + "." + tableName);
		}

		List<GenTableColumn> tableFieldList = metadata.columns();

		// 初始化字段数据
		columnService.initFieldList(tableFieldList);
		// 保存列数据
		if (!columnService.saveOrUpdateBatch(tableFieldList)) {
			throw new IllegalStateException("生成表字段导入失败: " + dsName + "." + tableName);
		}

		table.setFieldList(tableFieldList);
		return table;
	}

	protected TableMetadataSnapshot loadTableMetadata(String dsName, String tableName) {
		validateTableIdentity(dsName, tableName);
		DynamicDataSourceContextHolder.push(dsName);
		try {
			CacheProxy.clear();
			AnylineService service = ServiceProxy.service();
			Table tableMetadata = service.metadata().table(tableName);
			if (tableMetadata == null) {
				throw new IllegalArgumentException("物理表不存在: " + dsName + "." + tableName);
			}
			Database database = service.metadata().database();
			return new TableMetadataSnapshot(tableMetadata.getComment(), database.getDatabase().title(),
				getGenTableColumnEntities(dsName, tableName, tableMetadata));
		}
		finally {
			DynamicDataSourceContextHolder.poll();
		}
	}

	private static void validateTableIdentity(String dsName, String tableName) {
		if (StrUtil.isBlank(dsName) || StrUtil.isBlank(tableName)) {
			throw new IllegalArgumentException("数据源和表名不能为空");
		}
	}

	/**
	 * 获取表字段信息
	 * @param dsName 数据源信息
	 * @param tableName 表名称
	 * @param tableMetadata 表的元数据
	 * @return list
	 */
	private static @NotNull List<GenTableColumn> getGenTableColumnEntities(String dsName, String tableName,
			Table tableMetadata) {
		List<GenTableColumn> tableFieldList = new ArrayList<>();
		LinkedHashMap<String, Column> columns = tableMetadata.getColumns();
		columns.forEach((columnName, column) -> {
			GenTableColumn genTableColumn = new GenTableColumn();
			genTableColumn.setTableName(tableName);
			genTableColumn.setDsName(dsName);
			genTableColumn.setFieldName(column.getName());
			genTableColumn.setFieldComment(column.getComment());
			genTableColumn.setFieldType(column.getTypeName());
			genTableColumn.setPrimaryPk(
					column.isPrimaryKey() == 1 ? BoolFillEnum.TRUE.getValue() : BoolFillEnum.FALSE.getValue());
			genTableColumn.setAutoFill(AutoFillEnum.DEFAULT.name());
			genTableColumn.setFormItem(BoolFillEnum.TRUE.getValue());
			genTableColumn.setGridItem(BoolFillEnum.TRUE.getValue());

			// 审计字段处理
			CommonColumnFiledEnum commonColumnFiledEnum = commonColumnPolicy(column.getName());
			if (commonColumnFiledEnum != null) {
				genTableColumn.setFormItem(commonColumnFiledEnum.getFormItem());
				genTableColumn.setGridItem(commonColumnFiledEnum.getGridItem());
				genTableColumn.setAutoFill(commonColumnFiledEnum.getAutoFill());
				genTableColumn.setSn(commonColumnFiledEnum.getSort());
			}
			tableFieldList.add(genTableColumn);
		});
		return tableFieldList;
	}

	private static CommonColumnFiledEnum commonColumnPolicy(String fieldName) {
		if (fieldName == null) return null;
		for (CommonColumnFiledEnum policy : CommonColumnFiledEnum.values()) {
			if (policy.name().equalsIgnoreCase(fieldName.trim())) return policy;
		}
		return null;
	}

	protected record TableMetadataSnapshot(String tableComment, String dbType, List<GenTableColumn> columns) {

		protected TableMetadataSnapshot {
			columns = List.copyOf(columns);
		}

	}

	private record ColumnReconciliation(List<GenTableColumn> updates, List<GenTableColumn> inserts,
			List<Long> droppedIds, List<GenTableColumn> currentColumns) {
	}

}
