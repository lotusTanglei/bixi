package com.lotus.bixi.generator.service.impl;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.generator.config.BixiGeneratorDefaultProperties;
import com.lotus.bixi.generator.dto.GenTableImportResult;
import com.lotus.bixi.generator.entity.GenTable;
import com.lotus.bixi.generator.entity.GenTableColumn;
import com.lotus.bixi.generator.mapper.GenTableMapper;
import com.lotus.bixi.generator.service.GenGroupService;
import com.lotus.bixi.generator.service.GenTableColumnService;
import com.lotus.bixi.generator.template.BuiltInTemplateCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.anyline.metadata.Column;
import org.anyline.metadata.Table;
import org.anyline.proxy.ServiceProxy;
import org.anyline.service.AnylineService;
import org.anyline.service.AnylineService.DDLService;
import org.anyline.service.AnylineService.MetaDataService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GenTableServiceImplTest {

	private GenTableMapper mapper;
	private GenTableColumnService columnService;
	private GenGroupService groupService;
	private GenTableServiceImpl service;

	@BeforeEach
	void setUp() throws Exception {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), GenTable.class.getName()),
			GenTable.class);
		mapper = mock(GenTableMapper.class);
		columnService = mock(GenTableColumnService.class);
		groupService = mock(GenGroupService.class);
		service = spy(new GenTableServiceImpl(mock(BixiGeneratorDefaultProperties.class), columnService, groupService));
		Field baseMapper = GenTableServiceImpl.class.getSuperclass().getSuperclass().getDeclaredField("baseMapper");
		baseMapper.setAccessible(true);
		baseMapper.set(service, mapper);
		doNothing().when(service).updatePhysicalTableMetadata(any(GenTable.class));
		when(columnService.list(any(Wrapper.class))).thenReturn(List.of());
		when(columnService.removeByIds(anyCollection())).thenReturn(true);
		when(columnService.updatePhysicalMetadataById(any(GenTableColumn.class))).thenReturn(true);
		when(columnService.saveBatch(anyCollection())).thenReturn(true);
		when(groupService.list()).thenReturn(List.of());
	}

	@Test
	void exactConfigurationLookupDoesNotImportOrLoadColumns() {
		GenTable configured = configuredTable();
		when(mapper.selectOne(any())).thenReturn(configured);

		assertThat(service.findConfiguredTable("master", "purchase_order")).isSameAs(configured);

		verify(mapper).selectOne(any());
		verifyNoInteractions(columnService, groupService);
	}

	@Test
	void exactConfigurationDetailsLookupLoadsColumnsWithoutImporting() {
		GenTable configured = configuredTable();
		GenTableColumn persisted = configuredColumn(101L, "id");
		when(mapper.selectOne(any())).thenReturn(configured);
		when(columnService.list(any(Wrapper.class))).thenReturn(List.of(persisted));

		GenTable details = service.findConfiguredTableDetails("master", "purchase_order");

		assertThat(details).isSameAs(configured);
		assertThat(details.getFieldList()).containsExactly(persisted);
		verify(mapper).selectOne(any());
		verify(service, never()).tableImport(any(), any(), any());
	}

	@Test
	void exactConfigurationDetailsLookupReturnsNullWithoutLoadingDetailsWhenAbsent() {
		when(mapper.selectOne(any())).thenReturn(null);

		assertThat(service.findConfiguredTableDetails("master", "purchase_order")).isNull();

		verify(mapper).selectOne(any());
		verifyNoInteractions(columnService, groupService);
		verify(service, never()).tableImport(any(), any(), any());
	}

	@Test
	void exactConfigurationLookupRejectsBlankIdentity() {
		assertThatThrownBy(() -> service.findConfiguredTable(" ", "purchase_order"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("数据源和表名不能为空");
		assertThatThrownBy(() -> service.findConfiguredTable("master", ""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("数据源和表名不能为空");

		verifyNoInteractions(mapper, columnService, groupService);
	}

	@Test
	void explicitImportCreatesConfigurationWithTheRequestedOwnershipMarker() throws Exception {
		GenTableColumn physical = column("id", "BIGINT", "primary key");
		when(mapper.selectOne(any())).thenReturn(null);
		when(mapper.insert(any(GenTable.class))).thenAnswer(invocation -> {
			GenTable table = invocation.getArgument(0);
			table.setId(42L);
			return 1;
		});
		when(columnService.saveOrUpdateBatch(anyCollection())).thenReturn(true);
		doReturn(new GenTableServiceImpl.TableMetadataSnapshot("physical comment", "MySQL", List.of(physical)))
			.when(service).loadTableMetadata("master", "purchase_order");

		GenTableImportResult result = service.importTable("master", "purchase_order", "generator-acceptance");

		assertThat(result.created()).isTrue();
		assertThat(result.table().getId()).isEqualTo(42L);
		assertThat(result.table().getAuthor()).isEqualTo("generator-acceptance");
		assertThat(result.table().getStyle()).isEqualTo(BuiltInTemplateCatalog.DEFAULT_GROUP_ID);
		assertThat(result.table().getFieldList()).containsExactly(physical);
		verify(mapper).insert(any(GenTable.class));
		verify(service).tableImport("master", "purchase_order", "generator-acceptance");
		Transactional transaction = GenTableServiceImpl.class
			.getMethod("importTable", String.class, String.class, String.class)
			.getAnnotation(Transactional.class);
		assertThat(transaction).isNotNull();
		assertThat(transaction.rollbackFor()).contains(Exception.class);
	}

	@Test
	void explicitImportReturnsExistingConfigurationWithoutMutatingOrRemarkingIt() {
		GenTable existing = configuredTable();
		existing.setAuthor("foreign-owner");
		GenTableColumn persisted = configuredColumn(101L, "id");
		when(mapper.selectOne(any())).thenReturn(existing);
		when(columnService.list(any(Wrapper.class))).thenReturn(List.of(persisted));

		GenTableImportResult result = service.importTable("master", "purchase_order", "generator-acceptance");

		assertThat(result.created()).isFalse();
		assertThat(result.table()).isSameAs(existing);
		assertThat(result.table().getAuthor()).isEqualTo("foreign-owner");
		assertThat(result.table().getFieldList()).containsExactly(persisted);
		verify(service, never()).tableImport(any(), any(), any());
		verify(service, never()).updatePhysicalTableMetadata(any());
	}

	@Test
	void explicitImportRejectsInvalidOwnershipMarkerBeforeReadingOrWriting() {
		assertThatThrownBy(() -> service.importTable("master", "purchase_order", " "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("归属标记无效");
		assertThatThrownBy(() -> service.importTable("master", "purchase_order", "marker with spaces"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("归属标记无效");

		verifyNoInteractions(mapper, columnService, groupService);
	}

	@Test
	void concurrentImportConflictFailsWithoutLoadingOrRemarkingTheWinningRow() {
		when(mapper.selectOne(any())).thenReturn(null);
		DuplicateKeyException collision = new DuplicateKeyException("concurrent insert");
		doThrow(collision).when(service).tableImport("master", "purchase_order", "generator-acceptance");

		assertThatThrownBy(() -> service.importTable("master", "purchase_order", "generator-acceptance"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("并发导入冲突")
			.hasCause(collision);

		verify(mapper).selectOne(any());
		verify(service).tableImport("master", "purchase_order", "generator-acceptance");
		verify(service, never()).updatePhysicalTableMetadata(any());
	}

	@Test
	void metadataLoadRestoresTheCallingDatasourceContext() {
		AnylineService anyline = mock(AnylineService.class, RETURNS_DEEP_STUBS);
		Table physicalTable = mock(Table.class);
		when(physicalTable.getComment()).thenReturn("comment");
		when(physicalTable.getColumns()).thenReturn(new LinkedHashMap<>());
		when(anyline.metadata().table("purchase_order")).thenReturn(physicalTable);
		when(anyline.metadata().database().getDatabase().title()).thenReturn("MySQL");

		DynamicDataSourceContextHolder.push("outer");
		try (var serviceProxy = mockStatic(ServiceProxy.class)) {
			serviceProxy.when(ServiceProxy::service).thenReturn(anyline);

			service.loadTableMetadata("master", "purchase_order");

			assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("outer");
		}
		finally {
			DynamicDataSourceContextHolder.clear();
		}
	}

	@Test
	void metadataImportAppliesCommonColumnPoliciesRegardlessOfPhysicalNameCase() {
		AnylineService anyline = mock(AnylineService.class, RETURNS_DEEP_STUBS);
		Table physicalTable = mock(Table.class);
		Column id = physicalColumn("ID", "BIGINT", 1);
		Column created = physicalColumn("CREATE_TIME", "DATETIME", 0);
		Column business = physicalColumn("ORDER_NO", "VARCHAR", 0);
		LinkedHashMap<String, Column> columns = new LinkedHashMap<>();
		columns.put("ID", id);
		columns.put("CREATE_TIME", created);
		columns.put("ORDER_NO", business);
		when(physicalTable.getComment()).thenReturn("orders");
		when(physicalTable.getColumns()).thenReturn(columns);
		when(anyline.metadata().table("purchase_order")).thenReturn(physicalTable);
		when(anyline.metadata().database().getDatabase().title()).thenReturn("PostgreSQL");

		try (var serviceProxy = mockStatic(ServiceProxy.class)) {
			serviceProxy.when(ServiceProxy::service).thenReturn(anyline);

			List<GenTableColumn> imported = service.loadTableMetadata("master", "purchase_order").columns();

			GenTableColumn importedId = imported.get(0);
			assertThat(importedId.getPrimaryPk()).isEqualTo("1");
			GenTableColumn importedCreated = imported.get(1);
			assertThat(importedCreated.getFormItem()).isEqualTo("0");
			assertThat(importedCreated.getGridItem()).isEqualTo("0");
			assertThat(importedCreated.getAutoFill()).isEqualTo("INSERT");
			assertThat(importedCreated.getSn()).isEqualTo(101);
			assertThat(imported.get(2).getFieldName()).isEqualTo("ORDER_NO");
		}
	}

	private static Column physicalColumn(String name, String type, int primaryKey) {
		Column column = mock(Column.class);
		when(column.getName()).thenReturn(name);
		when(column.getTypeName()).thenReturn(type);
		when(column.getComment()).thenReturn(name);
		when(column.isPrimaryKey()).thenReturn(primaryKey);
		return column;
	}

	@Test
	void physicalMetadataQueriesRestoreTheCallingDatasourceContext() throws Exception {
		AnylineService anyline = mock(AnylineService.class, RETURNS_DEEP_STUBS);
		MetaDataService metadata = mock(MetaDataService.class, RETURNS_DEEP_STUBS);
		DDLService ddl = mock(DDLService.class, RETURNS_DEEP_STUBS);
		Table physicalTable = mock(Table.class);
		when(physicalTable.getDdl()).thenReturn("CREATE TABLE purchase_order (id BIGINT)");
		when(physicalTable.getName()).thenReturn("purchase_order");
		when(physicalTable.getName(false)).thenReturn("purchase_order");
		LinkedHashMap<String, Table> tables = new LinkedHashMap<>();
		tables.put("purchase_order", physicalTable);
		when(metadata.table("purchase_order")).thenReturn(physicalTable);
		when(metadata.columns("purchase_order")).thenReturn(new LinkedHashMap<>());
		when(metadata.tables()).thenReturn(tables);
		when(ddl.create(physicalTable)).thenReturn(true);

		DynamicDataSourceContextHolder.push("outer");
		try (var serviceProxy = mockStatic(ServiceProxy.class)) {
			serviceProxy.when(ServiceProxy::service).thenReturn(anyline);
			serviceProxy.when(ServiceProxy::metadata).thenReturn(metadata);
			serviceProxy.when(ServiceProxy::ddl).thenReturn(ddl);

			assertThat(service.queryTableDdl("master", "purchase_order")).contains("CREATE TABLE");
			assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("outer");
			assertThat(service.queryTableColumn("master", "purchase_order")).isEmpty();
			assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("outer");
			assertThat(service.queryTableList("master")).containsExactly("purchase_order");
			assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("outer");

			Page<Table> page = new Page<>(1, 10);
			assertThat(service.queryTablePage(page, configuredTable())).isSameAs(page);
			assertThat(page.getRecords()).containsExactly(physicalTable);
			assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("outer");
		}
		finally {
			DynamicDataSourceContextHolder.clear();
		}
	}

	@Test
	void physicalMetadataQueriesRestoreTheCallingDatasourceContextWhenMetadataFails() {
		MetaDataService metadata = mock(MetaDataService.class);
		RuntimeException failure = new IllegalStateException("metadata unavailable");
		when(metadata.tables()).thenThrow(failure);

		DynamicDataSourceContextHolder.push("outer");
		try (var serviceProxy = mockStatic(ServiceProxy.class)) {
			serviceProxy.when(ServiceProxy::metadata).thenReturn(metadata);

			assertThatThrownBy(() -> service.queryTableList("master"))
				.isSameAs(failure);
			assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("outer");
		}
		finally {
			DynamicDataSourceContextHolder.clear();
		}
	}

	@Test
	void synchronizationReconcilesColumnsAndPreservesUserConfigurationAndIdentity() {
		GenTable configured = configuredTable();
		GenTableColumn persisted = configuredColumn(101L, "Customer_ID");
		GenTableColumn dropped = configuredColumn(102L, "removed_field");
		GenTableColumn physical = physicalColumn("customer_id", "BIGINT", "current physical comment", "1",
			"customerId", "Long", null);
		GenTableColumn added = physicalColumn("created_at", "TIMESTAMP", "new physical column", "0",
			"createdAt", "LocalDateTime", "java.time.LocalDateTime");
		when(mapper.selectOne(any())).thenReturn(configured);
		when(columnService.list(any(Wrapper.class))).thenReturn(List.of(persisted, dropped));
		doReturn(new GenTableServiceImpl.TableMetadataSnapshot(
			"current physical comment", "MySQL", List.of(physical, added)))
			.when(service).loadTableMetadata("master", "purchase_order");

		GenTable result = service.syncTable("master", "purchase_order");

		assertThat(result).isSameAs(configured);
		assertThat(result.getId()).isEqualTo(41L);
		assertThat(result.getTableComment()).isEqualTo("current physical comment");
		assertThat(result.getDbType()).isEqualTo("MySQL");
		assertThat(result.getFieldList()).containsExactly(persisted, added);
		assertThat(result.getAuthor()).isEqualTo("generator-owner");
		assertThat(result.getPackageName()).isEqualTo("com.example.orders");
		assertThat(result.getClassName()).isEqualTo("PurchaseOrderCustom");
		assertThat(result.getModuleName()).isEqualTo("purchasing");
		assertThat(result.getFunctionName()).isEqualTo("purchaseOrderAggregate");
		assertThat(result.getStyle()).isEqualTo(88L);
		assertThat(result.getGeneratorType()).isEqualTo("1");
		assertThat(result.getChildTableName()).isEqualTo("purchase_order_item");
		assertThat(result.getMainField()).isEqualTo("id");
		assertThat(result.getChildField()).isEqualTo("order_id");
		assertThat(result.getRemark()).isEqualTo("keep this note");
		assertThat(result.getCreateBy()).isEqualTo(7L);
		assertThat(result.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 8, 30));

		assertThat(persisted.getId()).isEqualTo(101L);
		assertThat(persisted.getCreateBy()).isEqualTo(11L);
		assertThat(persisted.getUpdateBy()).isEqualTo(12L);
		assertThat(persisted.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 8, 1, 9, 10));
		assertThat(persisted.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 8, 2, 10, 11));
		assertThat(persisted.getDelFlag()).isEqualTo("0");
		assertThat(persisted.getStatus()).isEqualTo("active");
		assertThat(persisted.getDataStatus()).isEqualTo("ready");
		assertThat(persisted.getTenantId()).isEqualTo(13L);
		assertThat(persisted.getRemark()).isEqualTo("column note");
		assertThat(persisted.getSn()).isEqualTo(37);
		assertThat(persisted.getAutoFill()).isEqualTo("INSERT_UPDATE");
		assertThat(persisted.getBaseField()).isEqualTo("1");
		assertThat(persisted.getFormItem()).isEqualTo("0");
		assertThat(persisted.getFormRequired()).isEqualTo("1");
		assertThat(persisted.getFormType()).isEqualTo("select");
		assertThat(persisted.getFormValidator()).isEqualTo("required");
		assertThat(persisted.getGridItem()).isEqualTo("0");
		assertThat(persisted.getGridSort()).isEqualTo("1");
		assertThat(persisted.getQueryItem()).isEqualTo("1");
		assertThat(persisted.getQueryType()).isEqualTo("LIKE");
		assertThat(persisted.getQueryFormType()).isEqualTo("tree-select");
		assertThat(persisted.getFieldDict()).isEqualTo("customer_dict");
		assertThat(persisted.getFieldName()).isEqualTo("customer_id");
		assertThat(persisted.getFieldType()).isEqualTo("BIGINT");
		assertThat(persisted.getFieldComment()).isEqualTo("current physical comment");
		assertThat(persisted.getPrimaryPk()).isEqualTo("1");
		assertThat(persisted.getAttrName()).isEqualTo("customerId");
		assertThat(persisted.getAttrType()).isEqualTo("Long");
		assertThat(persisted.getPackageName()).isNull();

		var ordered = inOrder(service, columnService);
		ordered.verify(service).updatePhysicalTableMetadata(configured);
		ordered.verify(columnService).initFieldList(List.of(physical, added));
		ordered.verify(columnService).updatePhysicalMetadataById(persisted);
		ordered.verify(columnService).removeByIds(List.of(102L));
		ordered.verify(columnService).saveBatch(List.of(added));
	}

	@Test
	void synchronizationLocksConfigurationBeforeReadingTheColumnSnapshot() {
		GenTable configured = configuredTable();
		when(mapper.selectOne(any())).thenReturn(configured);
		doReturn(new GenTableServiceImpl.TableMetadataSnapshot("comment", "MySQL", List.of()))
			.when(service).loadTableMetadata("master", "purchase_order");

		service.syncTable("master", "purchase_order");

		@SuppressWarnings("rawtypes")
		ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
		var ordered = inOrder(mapper, columnService);
		ordered.verify(mapper).selectOne(query.capture());
		ordered.verify(columnService).list(any(Wrapper.class));
		assertThat(query.getValue().getSqlSegment()).containsIgnoringCase("FOR UPDATE");
	}

	@Test
	void synchronizationRejectsMissingConfigurationWithoutImportingOrWriting() {
		when(mapper.selectOne(any())).thenReturn(null);

		assertThatThrownBy(() -> service.syncTable("master", "purchase_order"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("生成表配置不存在")
			.hasMessageContaining("master.purchase_order");

		verify(service, never()).loadTableMetadata(any(), any());
		verify(service, never()).updatePhysicalTableMetadata(any(GenTable.class));
		verifyNoInteractions(columnService, groupService);
	}

	@Test
	void unsuccessfulTableMetadataUpdateIsRejectedBeforeColumnMutation() {
		GenTable configured = configuredTable();
		GenTableColumn persisted = configuredColumn(101L, "id");
		GenTableColumn physical = physicalColumn("id", "BIGINT", "id", "1", "id", "Long", null);
		when(mapper.selectOne(any())).thenReturn(configured);
		when(mapper.update(any(), any())).thenReturn(0);
		when(columnService.list(any(Wrapper.class))).thenReturn(List.of(persisted));
		doReturn(new GenTableServiceImpl.TableMetadataSnapshot("comment", "MySQL", List.of(physical)))
			.when(service).loadTableMetadata("master", "purchase_order");
		doCallRealMethod().when(service).updatePhysicalTableMetadata(configured);

		assertThatThrownBy(() -> service.syncTable("master", "purchase_order"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("配置更新失败")
			.hasMessageContaining("master.purchase_order");

		verify(columnService, never()).initFieldList(any());
		verify(columnService, never()).updatePhysicalMetadataById(any());
		verify(columnService, never()).removeByIds(anyCollection());
		verify(columnService, never()).saveBatch(anyCollection());
	}

	@Test
	void columnRefreshFailurePropagatesFromTransactionalSynchronizationBoundary() throws Exception {
		GenTable configured = configuredTable();
		GenTableColumn replacement = column("id", "BIGINT", "id");
		when(mapper.selectOne(any())).thenReturn(configured);
		doReturn(new GenTableServiceImpl.TableMetadataSnapshot("comment", "MySQL", List.of(replacement)))
			.when(service).loadTableMetadata("master", "purchase_order");
		RuntimeException failure = new RuntimeException("column write failed");
		doThrow(failure).when(columnService).saveBatch(anyCollection());

		assertThatThrownBy(() -> service.syncTable("master", "purchase_order")).isSameAs(failure);

		Transactional transaction = GenTableServiceImpl.class
			.getMethod("syncTable", String.class, String.class)
			.getAnnotation(Transactional.class);
		assertThat(transaction).isNotNull();
		assertThat(transaction.rollbackFor()).contains(Exception.class);
		verify(service).updatePhysicalTableMetadata(configured);
		verify(columnService, never()).removeByIds(anyCollection());
	}

	@Test
	void unsuccessfulColumnUpdateIsRejectedSoTheTransactionRollsBack() {
		GenTable configured = configuredTable();
		GenTableColumn persisted = configuredColumn(101L, "id");
		GenTableColumn physical = physicalColumn("id", "BIGINT", "id", "1", "id", "Long", null);
		when(mapper.selectOne(any())).thenReturn(configured);
		when(columnService.list(any(Wrapper.class))).thenReturn(List.of(persisted));
		doReturn(new GenTableServiceImpl.TableMetadataSnapshot("comment", "MySQL", List.of(physical)))
			.when(service).loadTableMetadata("master", "purchase_order");
		when(columnService.updatePhysicalMetadataById(persisted)).thenReturn(false);

		assertThatThrownBy(() -> service.syncTable("master", "purchase_order"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("字段更新失败")
			.hasMessageContaining("master.purchase_order");
		verify(columnService, never()).removeByIds(anyCollection());
		verify(columnService, never()).saveBatch(anyCollection());
	}

	@Test
	void unsuccessfulDroppedColumnDeleteIsRejectedSoTheTransactionRollsBack() {
		GenTable configured = configuredTable();
		GenTableColumn dropped = configuredColumn(102L, "removed_field");
		when(mapper.selectOne(any())).thenReturn(configured);
		when(columnService.list(any(Wrapper.class))).thenReturn(List.of(dropped));
		doReturn(new GenTableServiceImpl.TableMetadataSnapshot("comment", "MySQL", List.of()))
			.when(service).loadTableMetadata("master", "purchase_order");
		when(columnService.removeByIds(anyCollection())).thenReturn(false);

		assertThatThrownBy(() -> service.syncTable("master", "purchase_order"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("字段删除失败")
			.hasMessageContaining("master.purchase_order");
		verify(columnService).removeByIds(List.of(102L));
		verify(columnService, never()).saveBatch(anyCollection());
	}

	@Test
	void unsuccessfulNewColumnInsertIsRejectedSoTheTransactionRollsBack() {
		GenTable configured = configuredTable();
		GenTableColumn replacement = column("id", "BIGINT", "id");
		when(mapper.selectOne(any())).thenReturn(configured);
		doReturn(new GenTableServiceImpl.TableMetadataSnapshot("comment", "MySQL", List.of(replacement)))
			.when(service).loadTableMetadata("master", "purchase_order");
		when(columnService.saveBatch(anyCollection())).thenReturn(false);

		assertThatThrownBy(() -> service.syncTable("master", "purchase_order"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("字段新增失败")
			.hasMessageContaining("master.purchase_order");
	}

	@Test
	void columnInitializationFailurePropagatesBeforeColumnMutations() {
		GenTable configured = configuredTable();
		GenTableColumn physical = column("id", "BIGINT", "id");
		when(mapper.selectOne(any())).thenReturn(configured);
		doReturn(new GenTableServiceImpl.TableMetadataSnapshot("comment", "MySQL", List.of(physical)))
			.when(service).loadTableMetadata("master", "purchase_order");
		RuntimeException failure = new RuntimeException("field initialization failed");
		doThrow(failure).when(columnService).initFieldList(any());

		assertThatThrownBy(() -> service.syncTable("master", "purchase_order")).isSameAs(failure);

		verify(columnService, never()).updatePhysicalMetadataById(any());
		verify(columnService, never()).removeByIds(anyCollection());
		verify(columnService, never()).saveBatch(anyCollection());
	}

	private static GenTable configuredTable() {
		GenTable table = new GenTable();
		table.setId(41L);
		table.setDsName("master");
		table.setTableName("purchase_order");
		table.setDbType("old db");
		table.setTableComment("old comment");
		table.setAuthor("generator-owner");
		table.setEmail("owner@example.com");
		table.setPackageName("com.example.orders");
		table.setVersion("9.9.9");
		table.setGeneratorType("1");
		table.setBackendPath("backend/custom");
		table.setFrontendPath("frontend/custom");
		table.setClassName("PurchaseOrderCustom");
		table.setModuleName("purchasing");
		table.setFunctionName("purchaseOrderAggregate");
		table.setFormLayout(2);
		table.setBaseclassId(77L);
		table.setStyle(88L);
		table.setChildTableName("purchase_order_item");
		table.setMainField("id");
		table.setChildField("order_id");
		table.setRemark("keep this note");
		table.setCreateBy(7L);
		table.setCreateTime(LocalDateTime.of(2026, 9, 1, 8, 30));
		return table;
	}

	private static GenTableColumn column(String name, String type, String comment) {
		GenTableColumn column = new GenTableColumn();
		column.setDsName("master");
		column.setTableName("purchase_order");
		column.setFieldName(name);
		column.setFieldType(type);
		column.setFieldComment(comment);
		return column;
	}

	private static GenTableColumn physicalColumn(String name, String type, String comment, String primaryPk,
			String attrName, String attrType, String packageName) {
		GenTableColumn column = column(name, type, comment);
		column.setPrimaryPk(primaryPk);
		column.setAttrName(attrName);
		column.setAttrType(attrType);
		column.setPackageName(packageName);
		return column;
	}

	private static GenTableColumn configuredColumn(long id, String name) {
		GenTableColumn column = physicalColumn(name, "VARCHAR", "custom comment", "0", "customName", "String",
			"legacy.package");
		column.setId(id);
		column.setSn(37);
		column.setAutoFill("INSERT_UPDATE");
		column.setBaseField("1");
		column.setFormItem("0");
		column.setFormRequired("1");
		column.setFormType("select");
		column.setFormValidator("required");
		column.setGridItem("0");
		column.setGridSort("1");
		column.setQueryItem("1");
		column.setQueryType("LIKE");
		column.setQueryFormType("tree-select");
		column.setFieldDict("customer_dict");
		column.setCreateBy(11L);
		column.setUpdateBy(12L);
		column.setCreateTime(LocalDateTime.of(2026, 8, 1, 9, 10));
		column.setUpdateTime(LocalDateTime.of(2026, 8, 2, 10, 11));
		column.setDelFlag("0");
		column.setStatus("active");
		column.setDataStatus("ready");
		column.setTenantId(13L);
		column.setRemark("column note");
		return column;
	}

}
