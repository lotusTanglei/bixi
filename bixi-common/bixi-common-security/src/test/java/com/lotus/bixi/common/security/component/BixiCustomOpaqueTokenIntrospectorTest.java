package com.lotus.bixi.common.security.component;

import cn.hutool.extra.spring.SpringUtil;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.security.service.BixiUserDetailsService;
import com.lotus.bixi.upms.api.dto.UserInfo;
import com.lotus.bixi.upms.api.entity.SysUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.MockedStatic;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BixiCustomOpaqueTokenIntrospectorTest {
    private static final String TOKEN = "opaque-bearer-that-must-never-appear-in-errors";
    private final OAuth2AuthorizationService store = mock(OAuth2AuthorizationService.class);
    private final BixiUserDetailsService users = mock(BixiUserDetailsService.class);
    private final BixiCustomOpaqueTokenIntrospector introspector = new BixiCustomOpaqueTokenIntrospector(store);
    private MockedStatic<SpringUtil> spring;

    @BeforeEach
    void services() {
        spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtil.getBeansOfType(BixiUserDetailsService.class)).thenReturn(Map.of("users", users));
        when(users.support(anyString(), anyString())).thenReturn(true);
        when(users.loadUserByUser(any())).thenReturn(user(41L, "active"));
    }

    @AfterEach
    void close() { spring.close(); }

    static Stream<Arguments> inactiveTokens() {
        return Stream.of("password", "mobile", "authorization_code", "client_credentials")
                .flatMap(grant -> Stream.of("expired", "invalidated", "future", "missing", "mismatch", "noExpiry")
                        .map(reason -> Arguments.of(grant, reason)));
    }

    @ParameterizedTest
    @MethodSource("inactiveTokens")
    void redisPresenceCannotMakeAnInactiveTokenValid(String grant, String reason) {
        when(store.findByToken(TOKEN, OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization(grant, reason).build());
        assertThatThrownBy(() -> introspector.introspect(TOKEN))
                .isInstanceOf(InvalidBearerTokenException.class).hasMessageNotContaining(TOKEN);
        verify(users, never()).loadUserByUser(any());
    }

    @Test
    void missingAuthorizationDoesNotExposeThePresentedToken() {
        assertThatThrownBy(() -> introspector.introspect(TOKEN))
                .isInstanceOf(InvalidBearerTokenException.class).hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"locked", "disabled", "accountExpired", "credentialsExpired"})
    void rejectsCurrentAccountState(String state) {
        when(store.findByToken(TOKEN, OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization("password", "active").build());
        when(users.loadUserByUser(any())).thenReturn(user(41L, state));
        assertThatThrownBy(() -> introspector.introspect(TOKEN))
                .isInstanceOf(InvalidBearerTokenException.class).hasMessageNotContaining(TOKEN);
    }

    @Test
    void reloadedIdentityMustMatchTheOriginalUserId() {
        when(store.findByToken(TOKEN, OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization("mobile", "active").build());
        when(users.loadUserByUser(any())).thenReturn(user(99L, "active"));
        assertThatThrownBy(() -> introspector.introspect(TOKEN)).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void returnsCurrentAuthoritiesWithoutMutatingTheCachedUser() {
        var cached = user(41L, "active");
        when(users.loadUserByUser(any())).thenReturn(cached);
        when(store.findByToken(TOKEN, OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization("password", "active").build());
        var result = introspector.introspect(TOKEN);
        assertThat(result).isNotSameAs(cached);
        assertThat(result.getAuthorities()).extracting("authority").containsExactly("current_permission");
        assertThat(result.getAttributes()).containsEntry(SecurityConstants.CLIENT_ID, "bixi");
        assertThat(cached.getAttributes()).doesNotContainKey(SecurityConstants.CLIENT_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"noPrincipal", "wrongPrincipal", "noProvider", "missingUser", "nullUser"})
    void malformedOrUnresolvableUserSessionsFailClosed(String reason) {
        var builder = authorization("password", "active");
        if (reason.equals("noPrincipal")) builder.attributes(attributes -> attributes.remove(Principal.class.getName()));
        if (reason.equals("wrongPrincipal")) builder.attribute(Principal.class.getName(), "broken");
        if (reason.equals("noProvider")) when(users.support(anyString(), anyString())).thenReturn(false);
        if (reason.equals("missingUser")) when(users.loadUserByUser(any())).thenThrow(new UsernameNotFoundException(TOKEN));
        if (reason.equals("nullUser")) when(users.loadUserByUser(any())).thenReturn(null);
        when(store.findByToken(TOKEN, OAuth2TokenType.ACCESS_TOKEN)).thenReturn(builder.build());
        assertThatThrownBy(() -> introspector.introspect(TOKEN))
                .isInstanceOfAny(InvalidBearerTokenException.class, OAuth2IntrospectionException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"redis", "directory"})
    void dependencyFailureIsASanitizedAuthenticationFailure(String dependency) {
        if (dependency.equals("redis")) {
            when(store.findByToken(TOKEN, OAuth2TokenType.ACCESS_TOKEN)).thenThrow(new IllegalStateException(TOKEN));
        } else {
            when(store.findByToken(TOKEN, OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization("password", "active").build());
            when(users.loadUserByUser(any())).thenThrow(new IllegalStateException(TOKEN));
        }
        assertThatThrownBy(() -> introspector.introspect(TOKEN))
                .isInstanceOf(OAuth2IntrospectionException.class).hasMessageNotContaining(TOKEN);
    }

    @Test
    void validClientTokenWithoutOptionalClaimsDoesNotNeedAUserDirectory() {
        var builder = authorization("client_credentials", "active");
        var access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, TOKEN,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600), Set.of("server"));
        builder.token(access, metadata -> metadata.remove(OAuth2Authorization.Token.CLAIMS_METADATA_NAME));
        when(store.findByToken(TOKEN, OAuth2TokenType.ACCESS_TOKEN)).thenReturn(builder.build());
        assertThatCode(() -> {
            var principal = introspector.introspect(TOKEN);
            assertThat(principal.getName()).isEqualTo("alice");
            assertThat(principal.getAuthorities()).isEmpty();
        }).doesNotThrowAnyException();
        verify(users, never()).loadUserByUser(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1"})
    void databaseStatusControlsWhetherTheUserIsEnabled(String status) {
        var row = new SysUser();
        row.setId(41L);
        row.setUsername("alice");
        row.setPassword("encoded");
        row.setStatus(status);
        row.setLockFlag("0");
        var info = new UserInfo();
        info.setSysUser(row);
        BixiUserDetailsService factory = new BixiUserDetailsService() {
            @Override public UserDetails loadUserByUsername(String username) { throw new UnsupportedOperationException(); }
        };
        assertThat(factory.getUserDetails(R.ok(info)).isEnabled()).isEqualTo(status.equals("0"));
    }

    private static BixiUser user(Long id, String state) {
        return new BixiUser(id, 1L, "alice", "encoded", "13800000000", !state.equals("disabled"),
                !state.equals("accountExpired"), !state.equals("credentialsExpired"), !state.equals("locked"),
                AuthorityUtils.createAuthorityList("current_permission"));
    }

    private static OAuth2Authorization.Builder authorization(String grant, String state) {
        var client = RegisteredClient.withId("bixi").clientId("bixi")
                .authorizationGrantType(new AuthorizationGrantType(grant))
                .redirectUri("https://example.invalid/callback").build();
        var original = new BixiUser(41L, 1L, "alice", "encoded", "13800000000", true, true, true, true,
                AuthorityUtils.createAuthorityList("previous_permission"));
        var builder = OAuth2Authorization.withRegisteredClient(client).principalName("alice")
                .authorizationGrantType(new AuthorizationGrantType(grant)).authorizedScopes(Set.of("server"))
                .attribute(Principal.class.getName(), UsernamePasswordAuthenticationToken.authenticated(original, "", original.getAuthorities()));
        if (!state.equals("missing")) {
            var expiry = state.equals("noExpiry") ? null
                    : state.equals("expired") ? Instant.now().minusSeconds(1) : Instant.now().plusSeconds(3600);
            var access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                    state.equals("mismatch") ? "another-token" : TOKEN, Instant.now().minusSeconds(60), expiry, Set.of("server"));
            builder.token(access, metadata -> {
                metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, state.equals("future")
                        ? Map.of("nbf", Instant.now().plusSeconds(3600)) : Map.of("sub", "alice"));
                if (state.equals("invalidated")) metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true);
            });
        }
        return builder;
    }
}
