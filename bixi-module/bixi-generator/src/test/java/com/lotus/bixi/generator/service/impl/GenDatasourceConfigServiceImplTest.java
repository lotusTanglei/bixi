package com.lotus.bixi.generator.service.impl;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.creator.DataSourceCreator;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.generator.entity.GenDatasourceConfig;
import com.lotus.bixi.generator.mapper.GenDatasourceConfigMapper;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenDatasourceConfigServiceImplTest {

	private final StringEncryptor encryptor = mock(StringEncryptor.class);

	private final GenDatasourceConfigMapper mapper = mock(GenDatasourceConfigMapper.class);

	private final DynamicRoutingDataSource routingDataSource = mock(DynamicRoutingDataSource.class);

	private GenericApplicationContext context;

	private GenDatasourceConfigServiceImpl service;

	private AtomicReference<String> checkedPassword;

	private AtomicReference<String> installedPassword;

	@BeforeEach
	void setUp() {
		context = new GenericApplicationContext();
		context.getBeanFactory().registerSingleton("routingDataSource", routingDataSource);
		context.refresh();
		new SpringContextHolder().setApplicationContext(context);

		service = spy(new GenDatasourceConfigServiceImpl(encryptor, mock(DataSourceCreator.class)));
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		checkedPassword = new AtomicReference<>();
		installedPassword = new AtomicReference<>();
		doAnswer(invocation -> {
			checkedPassword.set(invocation.<GenDatasourceConfig>getArgument(0).getPassword());
			return Boolean.TRUE;
		}).when(service).checkDataSource(any(GenDatasourceConfig.class));
		doAnswer(invocation -> {
			installedPassword.set(invocation.<GenDatasourceConfig>getArgument(0).getPassword());
			return null;
		}).when(service).addDynamicDataSource(any(GenDatasourceConfig.class));
	}

	@AfterEach
	void tearDown() {
		SpringContextHolder.clearHolder();
		context.close();
	}

	@Test
	void omittedPasswordPreservesStoredCredentialForValidationRuntimeAndPersistence() {
		when(mapper.selectById(7L)).thenReturn(config(7L, "old-name", "encrypted-password"));
		when(encryptor.decrypt("encrypted-password")).thenReturn("plain-password");
		GenDatasourceConfig update = config(7L, "new-name", null);

		service.updateDsByEnc(update);

		assertThat(checkedPassword).hasValue("plain-password");
		assertThat(installedPassword).hasValue("plain-password");
		assertThat(update.getPassword()).isEqualTo("encrypted-password");
		verify(encryptor, never()).encrypt(any());
		verify(routingDataSource).removeDataSource("old-name");
		verify(mapper).updateById(update);
	}

	@Test
	void explicitPasswordReplacesStoredCredential() {
		when(mapper.selectById(7L)).thenReturn(config(7L, "old-name", "encrypted-password"));
		when(encryptor.encrypt("replacement-password")).thenReturn("new-encrypted-password");
		GenDatasourceConfig update = config(7L, "new-name", "replacement-password");

		service.updateDsByEnc(update);

		assertThat(checkedPassword).hasValue("replacement-password");
		assertThat(installedPassword).hasValue("replacement-password");
		assertThat(update.getPassword()).isEqualTo("new-encrypted-password");
		verify(encryptor, never()).decrypt(any());
		verify(mapper).updateById(update);
	}

	private static GenDatasourceConfig config(Long id, String name, String password) {
		GenDatasourceConfig config = new GenDatasourceConfig();
		config.setId(id);
		config.setName(name);
		config.setPassword(password);
		return config;
	}

}
