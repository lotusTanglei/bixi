package com.lotus.bixi.acceptance.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.acceptance.api.dto.SysDictItemDTO;
import com.lotus.bixi.acceptance.api.dto.SysDictQueryDTO;
import com.lotus.bixi.acceptance.api.dto.SysDictUpdateDTO;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamImportDTO;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamQueryDTO;
import com.lotus.bixi.acceptance.api.entity.SysDict;
import com.lotus.bixi.acceptance.api.entity.SysDictItem;
import com.lotus.bixi.acceptance.api.entity.SysPublicParam;
import com.lotus.bixi.acceptance.controller.SysDictController;
import com.lotus.bixi.acceptance.controller.SysPublicParamController;
import com.lotus.bixi.acceptance.mapper.SysDictItemMapper;
import com.lotus.bixi.acceptance.mapper.SysDictMapper;
import com.lotus.bixi.acceptance.mapper.SysPublicParamMapper;
import com.lotus.bixi.acceptance.service.impl.SysDictServiceImpl;
import com.lotus.bixi.acceptance.service.impl.SysPublicParamServiceImpl;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.feign.sentinel.handle.GlobalBizExceptionHandler;
import com.lotus.bixi.common.security.component.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AcceptanceServiceBehaviorTest {

	@Mock
	SysDictMapper dictMapper;

	@Mock
	SysDictItemMapper dictItemMapper;

	@Mock
	PermissionService permissionService;

	@Mock
	SysPublicParamMapper publicParamMapper;

	@InjectMocks
	SysDictServiceImpl dictService;

	@InjectMocks
	SysPublicParamServiceImpl publicParamService;

	@BeforeEach
	void setUp() {
		TenantContextHolder.set(1L);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "acceptance-test"), SysDict.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "acceptance-test"), SysDictItem.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "acceptance-test"), SysPublicParam.class);
		ReflectionTestUtils.setField(dictService, "baseMapper", dictMapper);
		ReflectionTestUtils.setField(publicParamService, "baseMapper", publicParamMapper);
		lenient().when(permissionService.hasPermission(any(String.class))).thenReturn(true);
	}

	@AfterEach
	void clearTenant() {
		TenantContextHolder.clear();
	}

	@Test
	void dictionaryPageCarriesTenantAndRequestedFilters() {
		when(dictMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
		SysDictQueryDTO query = new SysDictQueryDTO();
		query.setType("status");
		query.setName("Status");
		query.setSystemFlag("0");

		dictService.pageSysDict(new Page<>(1, 10), query);

		ArgumentCaptor<Wrapper<SysDict>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
		verify(dictMapper).selectPage(any(IPage.class), wrapper.capture());
		String sql = wrapper.getValue().getSqlSegment();
		assertThat(sql).contains("tenant_id", "type", "name", "system_flag");
	}

	@Test
	void publicParamPageCarriesTenantAndRequestedFilters() {
		when(publicParamMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
		SysPublicParamQueryDTO query = new SysPublicParamQueryDTO();
		query.setName("site");
		query.setKey("site.title");
		query.setType("2");
		query.setSystemFlag("0");

		publicParamService.pageSysPublicParam(new Page<>(1, 10), query);

		ArgumentCaptor<Wrapper<SysPublicParam>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
		verify(publicParamMapper).selectPage(any(IPage.class), wrapper.capture());
		String sql = wrapper.getValue().getSqlSegment();
		assertThat(sql).contains("tenant_id", "name", "key", "type", "system_flag");
	}

	@Test
	void publicParamDetailsRejectsARecordReturnedFromAnotherTenant() {
		SysPublicParam foreign = new SysPublicParam();
		foreign.setId(41L);
		foreign.setTenantId(2L);
		when(publicParamMapper.selectOne(any(), anyBoolean())).thenReturn(foreign);

		assertThatThrownBy(() -> publicParamService.details(41L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("不属于当前租户");
	}

	@Test
	void publicParamDeleteDoesNotTouchAnUnknownOrForeignId() {
		when(publicParamMapper.selectOne(any(), anyBoolean())).thenReturn(null);

		assertThatThrownBy(() -> publicParamService.delete(List.of(41L)))
			.isInstanceOf(IllegalArgumentException.class);
		verify(publicParamMapper, never()).deleteByIds(any());
	}

	@Test
	void publicParamImportPreservesKeyAndTenant() {
		when(publicParamMapper.insert(any(SysPublicParam.class))).thenReturn(1);
		SysPublicParamImportDTO row = new SysPublicParamImportDTO();
		row.setName("站点标题");
		row.setKey("site.title");
		row.setValue("Bixi");
		row.setType("2");
		row.setSystemFlag("0");

		assertThat(publicParamService.importRows(List.of(row)).isSuccess()).isTrue();

		ArgumentCaptor<SysPublicParam> entity = ArgumentCaptor.forClass(SysPublicParam.class);
		verify(publicParamMapper).insert(entity.capture());
		assertThat(entity.getValue().getKey()).isEqualTo("site.title");
		assertThat(entity.getValue().getTenantId()).isEqualTo(1L);
	}

	@Test
	void dictionaryUpdateRejectsAChildFromAnotherTenant() {
		SysDict parent = new SysDict();
		parent.setId(7L);
		parent.setTenantId(1L);
		when(dictMapper.selectOne(any(), anyBoolean())).thenReturn(parent);
		SysDictItem foreignChild = new SysDictItem();
		foreignChild.setId(8L);
		foreignChild.setDictId(7L);
		foreignChild.setTenantId(2L);
		when(dictItemMapper.selectOne(any())).thenReturn(foreignChild);

		SysDictUpdateDTO update = new SysDictUpdateDTO();
		update.setId(7L);
		SysDictItemDTO child = new SysDictItemDTO();
		child.setId(8L);
		child.setDictId(7L);
		update.setChildren(List.of(child));

		assertThatThrownBy(() -> dictService.update(update))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("不属于当前主表");
		verify(dictMapper, never()).updateById(any(SysDict.class));
	}

	@Test
	void duplicateDictionaryCreateReturnsSafeConflict() throws Exception {
		when(dictMapper.insert(any(SysDict.class))).thenThrow(databaseDuplicate());
		var http = MockMvcBuilders.standaloneSetup(new SysDictController(dictService))
			.setControllerAdvice(new GlobalBizExceptionHandler()).build();

		String body = http.perform(post("/dictAggregate").contentType("application/json")
				.content("{\"type\":\"status\",\"name\":\"Status\",\"children\":[]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(1))
			.andExpect(jsonPath("$.msg").value("字典类型已存在"))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("SQLIntegrityConstraintViolationException", "INSERT INTO", "uk_dict_tenant_type");
		verify(dictItemMapper, never()).insert(any(SysDictItem.class));
	}

	@Test
	void duplicateDictionaryUpdateReturnsSafeConflict() throws Exception {
		SysDict parent = new SysDict();
		parent.setId(7L);
		parent.setTenantId(1L);
		when(dictMapper.selectOne(any(), anyBoolean())).thenReturn(parent);
		when(dictMapper.updateById(any(SysDict.class))).thenThrow(databaseDuplicate());
		var http = MockMvcBuilders.standaloneSetup(new SysDictController(dictService))
			.setControllerAdvice(new GlobalBizExceptionHandler()).build();

		String body = http.perform(put("/dictAggregate").contentType("application/json")
				.content("{\"id\":7,\"type\":\"status\",\"name\":\"Status\",\"children\":[]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(1))
			.andExpect(jsonPath("$.msg").value("字典类型已存在"))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("SQLIntegrityConstraintViolationException", "INSERT INTO", "uk_dict_tenant_type");
	}

	@Test
	void duplicatePublicParamCreateReturnsSafeConflict() throws Exception {
		when(publicParamMapper.insert(any(SysPublicParam.class))).thenThrow(publicParamDatabaseDuplicate());
		var http = MockMvcBuilders.standaloneSetup(new SysPublicParamController(publicParamService))
			.setControllerAdvice(new GlobalBizExceptionHandler()).build();

		String body = http.perform(post("/publicParam").contentType("application/json")
				.content("{\"name\":\"Site title\",\"key\":\"site.title\",\"value\":\"Bixi\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(1))
			.andExpect(jsonPath("$.msg").value("公共参数键已存在"))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("SQLIntegrityConstraintViolationException", "INSERT INTO", "uk_public_param_tenant_active_key");
	}

	@Test
	void duplicatePublicParamUpdateReturnsSafeConflict() throws Exception {
		SysPublicParam current = new SysPublicParam();
		current.setId(7L);
		current.setTenantId(1L);
		when(publicParamMapper.selectOne(any(), anyBoolean())).thenReturn(current);
		when(publicParamMapper.updateById(any(SysPublicParam.class))).thenThrow(publicParamDatabaseDuplicate());
		var http = MockMvcBuilders.standaloneSetup(new SysPublicParamController(publicParamService))
			.setControllerAdvice(new GlobalBizExceptionHandler()).build();

		String body = http.perform(put("/publicParam").contentType("application/json")
				.content("{\"id\":7,\"name\":\"Site title\",\"key\":\"site.title\",\"value\":\"Bixi\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(1))
			.andExpect(jsonPath("$.msg").value("公共参数键已存在"))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("SQLIntegrityConstraintViolationException", "INSERT INTO", "uk_public_param_tenant_active_key");
	}

	private static DuplicateKeyException databaseDuplicate() {
		return new DuplicateKeyException("SQLIntegrityConstraintViolationException: "
			+ "Duplicate entry for key uk_dict_tenant_type; INSERT INTO sys_dict");
	}

	private static DuplicateKeyException publicParamDatabaseDuplicate() {
		return new DuplicateKeyException("SQLIntegrityConstraintViolationException: "
			+ "Duplicate entry for key uk_public_param_tenant_active_key; INSERT INTO sys_public_param");
	}
}
