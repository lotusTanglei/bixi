package com.lotus.bixi.upms.controller;

import com.lotus.bixi.upms.api.entity.SysPublicParam;
import com.lotus.bixi.upms.api.service.PublicParamQueryService;
import com.lotus.bixi.upms.service.SysPublicParamService;
import com.lotus.bixi.common.feign.sentinel.handle.GlobalBizExceptionHandler;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.common.security.annotation.Inner;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LegacyPublicParamControllerTest {

	@Mock SysPublicParamService service;
	@Mock PublicParamQueryService queryService;
	MockMvc http;

	@BeforeEach
	void setUp() {
		http = MockMvcBuilders.standaloneSetup(new SysPublicParamController(service, queryService))
			.setControllerAdvice(new GlobalBizExceptionHandler()).build();
	}

	@Test
	void publicLookupRequiresInternalCall() throws Exception {
		Inner inner = SysPublicParamController.class.getMethod("publicKey", String.class)
			.getAnnotation(Inner.class);
		assertThat(inner).isNotNull();
		assertThat(inner.value()).isTrue();
	}

	@Test
	void listAndDetailsRequireViewPermission() throws Exception {
		for (var method : new java.lang.reflect.Method[] {
			SysPublicParamController.class.getMethod("getSysPublicParamPage", Page.class, SysPublicParam.class),
			SysPublicParamController.class.getMethod("getById", Long.class),
			SysPublicParamController.class.getMethod("getDetail", SysPublicParam.class)
		}) {
			HasPermission permission = method.getAnnotation(HasPermission.class);
			assertThat(permission).as(method.getName()).isNotNull();
			assertThat(permission.value()).containsExactly("sys_syspublicparam_view");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	void createRejectsBlankKey(String key) throws Exception {
		http.perform(post("/param").contentType("application/json")
				.content("{\"key\":\"" + key + "\",\"name\":\"Name\",\"value\":\"Value\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(1))
			.andExpect(jsonPath("$.msg").value("参数键不能为空"));
		verify(service, never()).save(any(SysPublicParam.class));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	void updateRejectsBlankKey(String key) throws Exception {
		http.perform(put("/param").contentType("application/json")
				.content("{\"id\":7,\"key\":\"" + key + "\",\"name\":\"Name\",\"value\":\"Value\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(1))
			.andExpect(jsonPath("$.msg").value("参数键不能为空"));
		verify(service, never()).updateParam(any(SysPublicParam.class));
	}

	@Test
	void createDuplicateReturnsSafeConflict() throws Exception {
		when(service.save(any(SysPublicParam.class))).thenThrow(databaseDuplicate());

		String body = http.perform(post("/param").contentType("application/json")
				.content("{\"key\":\"SITE_TITLE\",\"name\":\"Name\",\"value\":\"Value\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(1))
			.andExpect(jsonPath("$.msg").value("参数键已存在"))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("SQLIntegrityConstraintViolationException", "INSERT INTO", "uk_public_param_tenant_active_key");
	}

	@Test
	void updateDuplicateReturnsSafeConflict() throws Exception {
		when(service.updateParam(any(SysPublicParam.class))).thenThrow(databaseDuplicate());

		String body = http.perform(put("/param").contentType("application/json")
				.content("{\"id\":7,\"key\":\"SITE_TITLE\",\"name\":\"Name\",\"value\":\"Value\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(1))
			.andExpect(jsonPath("$.msg").value("参数键已存在"))
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("SQLIntegrityConstraintViolationException", "UPDATE", "uk_public_param_tenant_active_key");
	}

	@Test
	void createWithoutSortKeepsLegacyContract() throws Exception {
		when(service.save(any(SysPublicParam.class))).thenReturn(true);

		http.perform(post("/param").contentType("application/json")
				.content("{\"key\":\"SITE_TITLE\",\"name\":\"Name\",\"value\":\"Value\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data").value(true));
	}

	private static DuplicateKeyException databaseDuplicate() {
		return new DuplicateKeyException("SQLIntegrityConstraintViolationException: "
			+ "Duplicate entry for key uk_public_param_tenant_active_key; INSERT INTO sys_public_param");
	}
}
