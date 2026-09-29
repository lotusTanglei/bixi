package com.lotus.bixi.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayReactiveOpaqueTokenIntrospectorTest {

    @Test
    void mapsAuthTokenResponseWithoutCopyingCredentialsIntoPrincipal() {
        AtomicReference<ClientRequest> request = new AtomicReference<>();
        ExchangeFunction exchange = capture(request, HttpStatus.OK, """
                {
                  "access_token":"opaque-token",
                  "token_type":"Bearer",
                  "expires_in":300,
                  "iat":1700000000.0,
                  "exp":1700003600.0,
                  "nbf":1699999990.0,
                  "scope":["bixi"],
                  "username":"alice",
                  "user_info":{"id":7,"deptId":9},
                  "refresh_token":"refresh-secret"
                }
                """);
        GatewaySecurityProperties properties = properties();
        GatewayReactiveOpaqueTokenIntrospector introspector = new GatewayReactiveOpaqueTokenIntrospector(
                WebClient.builder().exchangeFunction(exchange), properties);

        var principal = introspector.introspect("opaque-token").block();

        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo("alice");
        assertThat(principal.getAttributes()).containsEntry("active", true)
                .doesNotContainKeys("access_token", "refresh_token");
        Object issuedAt = principal.getAttribute("iat");
        Object expiresAt = principal.getAttribute("exp");
        Object notBefore = principal.getAttribute("nbf");
        assertThat(issuedAt).isEqualTo(Instant.ofEpochSecond(1_700_000_000L));
        assertThat(expiresAt).isEqualTo(Instant.ofEpochSecond(1_700_003_600L));
        assertThat(notBefore).isEqualTo(Instant.ofEpochSecond(1_699_999_990L));
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("SCOPE_bixi");
        assertThat(request.get().url().getQuery()).contains("token=opaque-token");
        assertThat(request.get().headers().getFirst("from")).isEqualTo("Y");
    }

    @Test
    void rejectsAResponseForAnotherToken() {
        ExchangeFunction exchange = capture(null, HttpStatus.OK,
                "{\"access_token\":\"different\",\"expires_in\":300,\"username\":\"alice\"}");
        GatewayReactiveOpaqueTokenIntrospector introspector = new GatewayReactiveOpaqueTokenIntrospector(
                WebClient.builder().exchangeFunction(exchange), properties());

        assertThatThrownBy(() -> introspector.introspect("opaque-token").block())
                .isInstanceOf(BadOpaqueTokenException.class)
                .hasMessage("Invalid bearer token");
    }

    @Test
    void mapsAuthClientErrorsToInvalidTokenAndServerErrorsToServiceFailure() {
        GatewayReactiveOpaqueTokenIntrospector invalid = new GatewayReactiveOpaqueTokenIntrospector(
                WebClient.builder().exchangeFunction(capture(null, HttpStatus.UNAUTHORIZED, "{}")), properties());
        GatewayReactiveOpaqueTokenIntrospector unavailable = new GatewayReactiveOpaqueTokenIntrospector(
                WebClient.builder().exchangeFunction(capture(null, HttpStatus.SERVICE_UNAVAILABLE, "{}")), properties());

        assertThatThrownBy(() -> invalid.introspect("opaque-token").block())
                .isInstanceOf(BadOpaqueTokenException.class);
        assertThatThrownBy(() -> unavailable.introspect("opaque-token").block())
                .isInstanceOf(OAuth2IntrospectionException.class)
                .isNotInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void rejectsEmptyTokenBeforeCallingAuth() {
        GatewayReactiveOpaqueTokenIntrospector introspector = new GatewayReactiveOpaqueTokenIntrospector(
                WebClient.builder().exchangeFunction(request -> Mono.error(new AssertionError("not called"))),
                properties());

        assertThatThrownBy(() -> introspector.introspect(" ").block())
                .isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void rejectsTemporalClaimsOutsideInstantRangeAsInvalidToken() {
        ExchangeFunction exchange = capture(null, HttpStatus.OK,
                "{\"access_token\":\"opaque-token\",\"expires_in\":300,"
                        + "\"username\":\"alice\",\"iat\":9223372036854775807}");
        GatewayReactiveOpaqueTokenIntrospector introspector = new GatewayReactiveOpaqueTokenIntrospector(
                WebClient.builder().exchangeFunction(exchange), properties());

        assertThatThrownBy(() -> introspector.introspect("opaque-token").block())
                .isInstanceOf(BadOpaqueTokenException.class)
                .hasMessage("Invalid bearer token");
    }

    private static GatewaySecurityProperties properties() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.setIntrospectionUri("http://auth.internal/token/check_token");
        properties.setRequestTimeout(Duration.ofSeconds(1));
        return properties;
    }

    private static ExchangeFunction capture(AtomicReference<ClientRequest> request,
                                            HttpStatus status,
                                            String body) {
        return clientRequest -> {
            if (request != null) {
                request.set(clientRequest);
            }
            return Mono.just(ClientResponse.create(status)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build());
        };
    }
}
