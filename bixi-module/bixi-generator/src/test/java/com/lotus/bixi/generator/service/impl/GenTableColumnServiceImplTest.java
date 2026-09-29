package com.lotus.bixi.generator.service.impl;

import com.lotus.bixi.generator.entity.GenTableColumn;
import com.lotus.bixi.generator.mapper.GenFieldTypeMapper;
import com.lotus.bixi.generator.mapper.GenTableColumnMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenTableColumnServiceImplTest {

	private GenTableColumnMapper mapper;
	private GenTableColumnServiceImpl service;

	@BeforeEach
	void setUp() throws Exception {
		mapper = mock(GenTableColumnMapper.class);
		service = spy(new GenTableColumnServiceImpl(mock(GenFieldTypeMapper.class)));
		Field baseMapper = GenTableColumnServiceImpl.class.getSuperclass().getSuperclass()
			.getDeclaredField("baseMapper");
		baseMapper.setAccessible(true);
		baseMapper.set(service, mapper);
		doReturn(true).when(service).updateBatchById(anyList());
	}

	@Test
	void updateKeepsImportedStructureWhenClientMarksANonPrimaryColumnAsPrimary() {
		GenTableColumn persisted = column(2L, "master", "purchase_order", "order_no", "VARCHAR", "0");
		when(mapper.selectList(any())).thenReturn(List.of(persisted));

		GenTableColumn request = column(2L, "other", "other_table", "id", "BIGINT", "1");
		request.setAttrName("forgedId");
		request.setAttrType("Long");
		request.setPackageName("forged.Type");
		request.setAutoFill("INSERT_UPDATE");
		request.setBaseField("1");
		request.setFieldComment("订单编号");
		request.setFormItem("1");
		request.setFormRequired("1");
		request.setFormType("text");
		request.setFormValidator("letterAndNumber");
		request.setGridItem("1");
		request.setGridSort("1");
		request.setQueryItem("1");
		request.setQueryType("like");
		request.setQueryFormType("text");
		request.setFieldDict("order_status");

		service.updateTableField("master", "purchase_order", List.of(request));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<GenTableColumn>> updates = ArgumentCaptor.forClass(List.class);
		verify(service).updateBatchById(updates.capture());
		GenTableColumn updated = updates.getValue().get(0);
		assertThat(updated.getId()).isEqualTo(2L);
		assertThat(updated.getDsName()).isEqualTo("master");
		assertThat(updated.getTableName()).isEqualTo("purchase_order");
		assertThat(updated.getFieldName()).isEqualTo("order_no");
		assertThat(updated.getFieldType()).isEqualTo("VARCHAR");
		assertThat(updated.getPrimaryPk()).isEqualTo("0");
		assertThat(updated.getAttrName()).isEqualTo("orderNo");
		assertThat(updated.getAttrType()).isEqualTo("String");
		assertThat(updated.getPackageName()).isNull();
		assertThat(updated.getAutoFill()).isEqualTo("DEFAULT");
		assertThat(updated.getBaseField()).isEqualTo("0");
		assertThat(updated.getFieldComment()).isEqualTo("订单编号");
		assertThat(updated.getFormRequired()).isEqualTo("1");
		assertThat(updated.getQueryType()).isEqualTo("like");
		assertThat(updated.getSn()).isZero();
	}

	@Test
	void rejectsNewOrCrossTableColumnIdsBeforeWriting() {
		when(mapper.selectList(any())).thenReturn(List.of(
			column(1L, "master", "purchase_order", "id", "BIGINT", "1")));
		GenTableColumn foreign = column(99L, "master", "purchase_order_item", "order_id", "BIGINT", "0");

		assertThatThrownBy(() -> service.updateTableField("master", "purchase_order", List.of(foreign)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("字段不属于当前表");

		verify(service, never()).updateBatchById(anyList());
	}

	private static GenTableColumn column(Long id, String dsName, String tableName, String fieldName,
			String fieldType, String primaryPk) {
		GenTableColumn column = new GenTableColumn();
		column.setId(id);
		column.setDsName(dsName);
		column.setTableName(tableName);
		column.setFieldName(fieldName);
		column.setFieldType(fieldType);
		column.setPrimaryPk(primaryPk);
		column.setAttrName("order_no".equals(fieldName) ? "orderNo" : fieldName);
		column.setAttrType("VARCHAR".equals(fieldType) ? "String" : "Long");
		column.setAutoFill("DEFAULT");
		column.setBaseField("0");
		return column;
	}

}
