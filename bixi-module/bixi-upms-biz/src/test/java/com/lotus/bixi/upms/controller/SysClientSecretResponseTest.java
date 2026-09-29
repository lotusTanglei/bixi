package com.lotus.bixi.upms.controller;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.entity.SysOauthClientDetails;
import com.lotus.bixi.upms.api.service.ClientDetailsQueryService;
import com.lotus.bixi.upms.service.SysOauthClientDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SysClientSecretResponseTest {

	private final SysOauthClientDetailsService clientDetailsService = mock(SysOauthClientDetailsService.class);

	private final ClientDetailsQueryService clientDetailsQueryService = mock(ClientDetailsQueryService.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	private MockMvc http;

	@BeforeEach
	void setUp() {
		http = MockMvcBuilders
				.standaloneSetup(new SysClientController(clientDetailsService, clientDetailsQueryService))
				.build();
	}

	@Test
	void managementDetailOmitsClientSecret() throws Exception {
		when(clientDetailsService.getOne(any())).thenReturn(client("detail-secret"));

		JsonNode body = responseBody("/client/bixi");

		assertThat(body.path("data").has("clientSecret")).isFalse();
		assertThat(body.toString()).doesNotContain("detail-secret");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void managementPageOmitsClientSecret() throws Exception {
		Page<SysOauthClientDetails> result = new Page<>();
		result.setRecords(List.of(client("page-secret")));
		when(clientDetailsService.page(any(Page.class), any())).thenReturn(result);

		JsonNode body = responseBody("/client/page");

		assertThat(body.path("data").path("records").path(0).has("clientSecret")).isFalse();
		assertThat(body.toString()).doesNotContain("page-secret");
	}

	@Test
	void internalOauthLookupStillReturnsClientSecret() throws Exception {
		when(clientDetailsQueryService.getClientDetailsById("bixi"))
				.thenReturn(R.ok(client("internal-secret")));

		JsonNode body = responseBody("/client/getClientDetailsById/bixi");

		assertThat(body.path("data").path("clientSecret").asText()).isEqualTo("internal-secret");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void pageIgnoresClientSecretQueryParameter() throws Exception {
		when(clientDetailsService.page(any(Page.class), any())).thenReturn(new Page<>());

		http.perform(get("/client/page").param("clientSecret", "url-secret"))
				.andExpect(status().isOk());

		var query = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
		verify(clientDetailsService).page(any(Page.class), query.capture());
		assertQueryDoesNotContain(query.getValue(), "url-secret");
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void exportIgnoresClientSecretQueryParameter() throws Exception {
		when(clientDetailsService.list((Wrapper<SysOauthClientDetails>) any(Wrapper.class))).thenReturn(List.of());

		http.perform(get("/client/export").param("clientSecret", "url-secret"))
				.andExpect(status().isOk());

		var query = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
		verify(clientDetailsService).list(query.capture());
		assertQueryDoesNotContain(query.getValue(), "url-secret");
	}

	@Test
	@SuppressWarnings("unchecked")
	void managementExportClearsClientSecret() {
		when(clientDetailsService.list((Wrapper<SysOauthClientDetails>) any(Wrapper.class)))
				.thenReturn(List.of(client("export-secret")));

		List<SysOauthClientDetails> exported = new SysClientController(clientDetailsService, clientDetailsQueryService)
				.export(null);

		assertThat(exported).extracting(SysOauthClientDetails::getClientSecret).containsOnlyNulls();
	}

	@Test
	void publicQueriesExposeOnlyClientIdAsTheirFilter() {
		Method page = method("getOauthClientDetailsPage");
		Method export = method("export");

		assertThat(page.getParameterTypes()).containsExactly(Page.class, String.class);
		assertThat(export.getParameterTypes()).containsExactly(String.class);
	}

	@Test
	void createStillRequiresClientSecret() throws Exception {
		http.perform(post("/client")
				.contentType("application/json")
				.content("{\"clientId\":\"bixi\",\"scope\":\"server\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateAcceptsAnOmittedClientSecret() throws Exception {
		when(clientDetailsService.updateClientById(any())).thenReturn(Boolean.TRUE);

		http.perform(put("/client")
				.contentType("application/json")
				.content("{\"id\":1,\"clientId\":\"bixi\",\"scope\":\"server\"}"))
				.andExpect(status().isOk());
	}

	private JsonNode responseBody(String path) throws Exception {
		String json = http.perform(get(path))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json);
	}

	private static SysOauthClientDetails client(String secret) {
		SysOauthClientDetails details = new SysOauthClientDetails();
		details.setClientId("bixi");
		details.setClientSecret(secret);
		details.setScope("server");
		return details;
	}

	private static void assertQueryDoesNotContain(Wrapper<?> query, String secret) {
		assertThat(query).isInstanceOf(AbstractWrapper.class);
		AbstractWrapper<?, ?, ?> abstractQuery = (AbstractWrapper<?, ?, ?>) query;
		assertThat(abstractQuery.getParamNameValuePairs()).doesNotContainValue(secret);
		if (query.getEntity() instanceof SysOauthClientDetails details) {
			assertThat(details.getClientSecret()).isNull();
		}
	}

	private static Method method(String name) {
		return java.util.Arrays.stream(SysClientController.class.getDeclaredMethods())
				.filter(method -> method.getName().equals(name))
				.findFirst()
				.orElseThrow();
	}

}
