package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamUpdateDTO;
import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.mapper.SysPublicParamMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(PublicParamCacheIsolationTest.Config.class)
class PublicParamCacheIsolationTest {
	@Autowired SysPublicParamService legacy;
	@Autowired com.lotus.bixi.acceptance.api.service.SysPublicParamService acceptance;
	@Autowired SysPublicParamMapper legacyMapper;
	@Autowired com.lotus.bixi.acceptance.mapper.SysPublicParamMapper acceptanceMapper;
	@Autowired CacheManager caches;
	private final Map<Long, Map<String, String>> values = new HashMap<>();

	@BeforeEach
	void setUp() {
		TenantContextHolder.set(1L);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "legacy-param-cache"),
			com.lotus.bixi.upms.api.entity.SysPublicParam.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "acceptance-param-cache"),
			com.lotus.bixi.acceptance.api.entity.SysPublicParam.class);
		reset(legacyMapper, acceptanceMapper);
		caches.getCache(CacheConstants.PARAMS_DETAILS).clear();
		values.clear();
		when(legacyMapper.selectOne(any())).thenAnswer(invocation -> {
			LambdaQueryWrapper<?> query = invocation.getArgument(0);
			query.getSqlSegment();
			String key = query.getParamNameValuePairs().values().iterator().next().toString();
			String value = values.getOrDefault(TenantContextHolder.get(), Map.of()).get(key);
			return value == null ? null : legacyParam(key, value);
		});
	}

	@AfterEach
	void clearTenant() {
		TenantContextHolder.clear();
	}

	@Test
	void sameKeyInTwoTenantsReturnsEachTenantsValue() {
		values.put(1L, Map.of("SHARED", "tenant-one"));
		values.put(2L, Map.of("SHARED", "tenant-two"));

		assertThat(legacy.getSysPublicParamKeyToValue("SHARED")).isEqualTo("tenant-one");
		TenantContextHolder.set(2L);
		assertThat(legacy.getSysPublicParamKeyToValue("SHARED")).isEqualTo("tenant-two");
	}

	@Test
	void renamingLegacyKeyEvictsItsOldCachedValue() {
		values.put(1L, new HashMap<>(Map.of("OLD", "before")));
		when(legacyMapper.selectById(7L)).thenReturn(legacyParam("OLD", "before"));
		when(legacyMapper.updateById(any(com.lotus.bixi.upms.api.entity.SysPublicParam.class))).thenAnswer(invocation -> {
			com.lotus.bixi.upms.api.entity.SysPublicParam changed = invocation.getArgument(0);
			values.get(1L).remove("OLD");
			values.get(1L).put(changed.getKey(), changed.getValue());
			return 1;
		});
		assertThat(legacy.getSysPublicParamKeyToValue("OLD")).isEqualTo("before");

		legacy.updateParam(legacyParam("NEW", "after"));

		assertThat(legacy.getSysPublicParamKeyToValue("OLD")).isNull();
		assertThat(legacy.getSysPublicParamKeyToValue("NEW")).isEqualTo("after");
	}

	@Test
	void acceptanceUpdateEvictsLegacyCachedValue() {
		values.put(1L, new HashMap<>(Map.of("SHARED", "before")));
		when(acceptanceMapper.selectOne(any(), anyBoolean())).thenReturn(acceptanceParam("SHARED", "before"));
		when(acceptanceMapper.updateById(any(com.lotus.bixi.acceptance.api.entity.SysPublicParam.class))).thenAnswer(invocation -> {
			com.lotus.bixi.acceptance.api.entity.SysPublicParam changed = invocation.getArgument(0);
			values.get(1L).put(changed.getKey(), changed.getValue());
			return 1;
		});
		assertThat(legacy.getSysPublicParamKeyToValue("SHARED")).isEqualTo("before");
		SysPublicParamUpdateDTO dto = new SysPublicParamUpdateDTO();
		dto.setId(7L);
		dto.setKey("SHARED");
		dto.setValue("after");

		assertThat(acceptance.update(dto)).isTrue();
		assertThat(legacy.getSysPublicParamKeyToValue("SHARED")).isEqualTo("after");
	}

	@Test
	void acceptanceDeleteEvictsLegacyCachedValue() {
		values.put(1L, new HashMap<>(Map.of("SHARED", "before")));
		when(acceptanceMapper.selectOne(any(), anyBoolean())).thenReturn(acceptanceParam("SHARED", "before"));
		when(acceptanceMapper.deleteByIds(any())).thenAnswer(invocation -> {
			values.get(1L).remove("SHARED");
			return 1;
		});
		assertThat(legacy.getSysPublicParamKeyToValue("SHARED")).isEqualTo("before");

		assertThat(acceptance.delete(List.of(7L))).isTrue();
		assertThat(legacy.getSysPublicParamKeyToValue("SHARED")).isNull();
	}

	private static com.lotus.bixi.upms.api.entity.SysPublicParam legacyParam(String key, String value) {
		var param = new com.lotus.bixi.upms.api.entity.SysPublicParam();
		param.setId(7L);
		param.setTenantId(TenantContextHolder.get());
		param.setKey(key);
		param.setValue(value);
		param.setSystemFlag("0");
		return param;
	}

	private static com.lotus.bixi.acceptance.api.entity.SysPublicParam acceptanceParam(String key, String value) {
		var param = new com.lotus.bixi.acceptance.api.entity.SysPublicParam();
		param.setId(7L);
		param.setTenantId(TenantContextHolder.get());
		param.setKey(key);
		param.setValue(value);
		return param;
	}

	@Configuration(proxyBeanMethods = false)
	@EnableCaching
	static class Config {
		@Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager(CacheConstants.PARAMS_DETAILS); }
		@Bean SysPublicParamMapper legacyMapper() { return mock(SysPublicParamMapper.class); }
		@Bean com.lotus.bixi.acceptance.mapper.SysPublicParamMapper acceptanceMapper() {
			return mock(com.lotus.bixi.acceptance.mapper.SysPublicParamMapper.class);
		}
		@Bean SysPublicParamService legacy(SysPublicParamMapper mapper) {
			var service = new com.lotus.bixi.upms.service.impl.SysPublicParamServiceImpl();
			ReflectionTestUtils.setField(service, "baseMapper", mapper);
			return service;
		}
		@Bean com.lotus.bixi.acceptance.api.service.SysPublicParamService acceptance(
				com.lotus.bixi.acceptance.mapper.SysPublicParamMapper mapper) {
			var service = new com.lotus.bixi.acceptance.service.impl.SysPublicParamServiceImpl();
			ReflectionTestUtils.setField(service, "baseMapper", mapper);
			return service;
		}
	}
}
