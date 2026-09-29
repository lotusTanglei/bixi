package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.upms.api.entity.SysOauthClientDetails;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class SysOauthClientDetailsServiceImplTest {

	@Test
	void omittedClientSecretPreservesStoredValue() {
		SysOauthClientDetailsServiceImpl service = spy(new SysOauthClientDetailsServiceImpl());
		SysOauthClientDetails stored = client(1L, "stored-secret");
		SysOauthClientDetails update = client(1L, null);
		doReturn(stored).when(service).getById(1L);
		doReturn(true).when(service).saveOrUpdate(any(SysOauthClientDetails.class));

		service.updateClientById(update);

		assertThat(update.getClientSecret()).isEqualTo("stored-secret");
	}

	@Test
	void explicitClientSecretReplacesStoredValue() {
		SysOauthClientDetailsServiceImpl service = spy(new SysOauthClientDetailsServiceImpl());
		SysOauthClientDetails update = client(1L, "replacement-secret");
		doReturn(true).when(service).saveOrUpdate(any(SysOauthClientDetails.class));

		service.updateClientById(update);

		assertThat(update.getClientSecret()).isEqualTo("replacement-secret");
		verify(service, never()).getById(1L);
	}

	@Test
	void omittedClientSecretDoesNotCreateMissingClient() {
		SysOauthClientDetailsServiceImpl service = spy(new SysOauthClientDetailsServiceImpl());
		SysOauthClientDetails update = client(99L, null);
		doReturn(null).when(service).getById(99L);
		doReturn(true).when(service).saveOrUpdate(any(SysOauthClientDetails.class));

		assertThat(service.updateClientById(update)).isFalse();

		verify(service, never()).saveOrUpdate(any(SysOauthClientDetails.class));
	}

	private static SysOauthClientDetails client(Long id, String secret) {
		SysOauthClientDetails details = new SysOauthClientDetails();
		details.setId(id);
		details.setClientSecret(secret);
		return details;
	}

}
