package com.lotus.bixi.auth.support.filter;

import cn.hutool.extra.spring.SpringUtil;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.security.service.BixiUserDetailsService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RefreshTokenAccountStatusFilterTest {

	private StaticApplicationContext context;
	private OAuth2AuthorizationService authorizationService;
	private RefreshTokenAccountStatusFilter filter;
	private MockHttpServletResponse response;
	private FilterChain chain;

	@BeforeEach
	void setUp() {
		context = new StaticApplicationContext();
		context.refresh();
		var springUtil = new SpringUtil();
		springUtil.setApplicationContext(context);
		springUtil.postProcessBeanFactory(context.getBeanFactory());

		authorizationService = mock(OAuth2AuthorizationService.class);
		filter = new RefreshTokenAccountStatusFilter(authorizationService);
		response = new MockHttpServletResponse();
		chain = mock(FilterChain.class);
	}

	@AfterEach
	void tearDown() {
		context.close();
	}

	private MockHttpServletRequest request(String path, String grantType, String refreshToken) {
		var request = new MockHttpServletRequest("POST", path);
		request.setRequestURI(path);
		request.setServletPath(path);
		if (grantType != null) {
			request.setParameter(OAuth2ParameterNames.GRANT_TYPE, grantType);
		}
		if (refreshToken != null) {
			request.setParameter(OAuth2ParameterNames.REFRESH_TOKEN, refreshToken);
		}
		return request;
	}

	private BixiUser bixiUser(Long id, String username, boolean enabled, boolean accountNonLocked) {
		return new BixiUser(id, 1L, 1L, username, "{noop}pass", "13800138000", enabled, true, true, accountNonLocked,
				List.of(new SimpleGrantedAuthority("ROLE_USER")));
	}

	private OAuth2Authorization authorization(String grantType, String clientId, BixiUser principal) {
		RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString()).clientId(clientId)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.redirectUri("http://localhost")
				.authorizationGrantType(new AuthorizationGrantType(grantType)).build();
		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token",
				Instant.now(), Instant.now().plusSeconds(3600));
		return OAuth2Authorization.withRegisteredClient(client).id(UUID.randomUUID().toString())
				.principalName(principal.getUsername())
				.authorizationGrantType(new AuthorizationGrantType(grantType))
				.token(accessToken)
				.attribute(Principal.class.getName(),
						UsernamePasswordAuthenticationToken.authenticated(principal, principal.getPassword(),
								principal.getAuthorities()))
				.build();
	}

	@Test
	void nonTokenEndpointPassesThrough() throws Exception {
		var request = request("/other/path", "refresh_token", "rt-123");

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(response.getStatus()).isNotEqualTo(400);
	}

	@Test
	void nonRefreshGrantPassesThrough() throws Exception {
		var request = request("/oauth2/token", "password", "rt-123");

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
	}

	@Test
	void missingRefreshTokenParameterPassesThrough() throws Exception {
		var request = request("/oauth2/token", "refresh_token", null);

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
	}

	@Test
	void unknownRefreshTokenPassesThrough() throws Exception {
		when(authorizationService.findByToken("rt-unknown", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(null);
		var request = request("/oauth2/token", "refresh_token", "rt-unknown");

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
	}

	@Test
	void clientCredentialsGrantSkipsValidation() throws Exception {
		var systemUser = bixiUser(99L, "system", true, true);
		var auth = authorization(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue(), "internal", systemUser);
		when(authorizationService.findByToken("rt-cc", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(auth);
		var request = request("/oauth2/token", "refresh_token", "rt-cc");

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
	}

	@Test
	void lockedUserIsRejectedWithInvalidGrant() throws Exception {
		var lockedUser = bixiUser(1L, "alice", true, false);
		var auth = authorization(AuthorizationGrantType.AUTHORIZATION_CODE.getValue(), "bixi", lockedUser);
		when(authorizationService.findByToken("rt-locked", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(auth);

		context.getBeanFactory().registerSingleton("userDetailsService", new BixiUserDetailsService() {
			@Override
			public boolean support(String clientId, String grant) {
				return true;
			}

			@Override
			public UserDetails loadUserByUsername(String name) {
				return bixiUser(1L, "alice", true, false);
			}

			@Override
			public UserDetails loadUserByUser(BixiUser user) {
				return bixiUser(1L, "alice", true, false);
			}
		});

		var request = request("/oauth2/token", "refresh_token", "rt-locked");

		filter.doFilter(request, response, chain);

		verify(chain, never()).doFilter(request, response);
		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentAsString()).contains("invalid_grant");
	}

	@Test
	void disabledUserIsRejectedWithInvalidGrant() throws Exception {
		var disabledUser = bixiUser(2L, "bob", false, true);
		var auth = authorization(AuthorizationGrantType.AUTHORIZATION_CODE.getValue(), "bixi", disabledUser);
		when(authorizationService.findByToken("rt-disabled", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(auth);

		context.getBeanFactory().registerSingleton("userDetailsService", new BixiUserDetailsService() {
			@Override
			public boolean support(String clientId, String grant) {
				return true;
			}

			@Override
			public UserDetails loadUserByUsername(String name) {
				return bixiUser(2L, "bob", false, true);
			}

			@Override
			public UserDetails loadUserByUser(BixiUser user) {
				return bixiUser(2L, "bob", false, true);
			}
		});

		var request = request("/oauth2/token", "refresh_token", "rt-disabled");

		filter.doFilter(request, response, chain);

		verify(chain, never()).doFilter(request, response);
		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentAsString()).contains("invalid_grant");
	}

	@Test
	void userIdentityChangeIsRejected() throws Exception {
		var originalUser = bixiUser(1L, "alice", true, true);
		var auth = authorization(AuthorizationGrantType.AUTHORIZATION_CODE.getValue(), "bixi", originalUser);
		when(authorizationService.findByToken("rt-changed", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(auth);

		context.getBeanFactory().registerSingleton("userDetailsService", new BixiUserDetailsService() {
			@Override
			public boolean support(String clientId, String grant) {
				return true;
			}

			@Override
			public UserDetails loadUserByUsername(String name) {
				return bixiUser(2L, "alice", true, true);
			}

			@Override
			public UserDetails loadUserByUser(BixiUser user) {
				return bixiUser(2L, "alice", true, true);
			}
		});

		var request = request("/oauth2/token", "refresh_token", "rt-changed");

		filter.doFilter(request, response, chain);

		verify(chain, never()).doFilter(request, response);
		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentAsString()).containsIgnoringCase("identity changed");
	}

	@Test
	void activeUserPassesThrough() throws Exception {
		var activeUser = bixiUser(1L, "alice", true, true);
		var auth = authorization(AuthorizationGrantType.AUTHORIZATION_CODE.getValue(), "bixi", activeUser);
		when(authorizationService.findByToken("rt-valid", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(auth);

		context.getBeanFactory().registerSingleton("userDetailsService", new BixiUserDetailsService() {
			@Override
			public boolean support(String clientId, String grant) {
				return true;
			}

			@Override
			public UserDetails loadUserByUsername(String name) {
				return bixiUser(1L, "alice", true, true);
			}

			@Override
			public UserDetails loadUserByUser(BixiUser user) {
				return bixiUser(1L, "alice", true, true);
			}
		});

		var request = request("/oauth2/token", "refresh_token", "rt-valid");

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(response.getStatus()).isNotEqualTo(400);
	}

}
