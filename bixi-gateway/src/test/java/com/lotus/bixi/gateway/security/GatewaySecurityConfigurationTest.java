package com.lotus.bixi.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Verifies the Gateway's reactive bearer boundary independently from route discovery.
 */
@WebFluxTest(controllers = GatewaySecurityConfigurationTest.Endpoint.class)
@Import({GatewaySecurityConfiguration.class, GatewaySecurityConfigurationTest.Endpoint.class})
class GatewaySecurityConfigurationTest {

    @Autowired
    private WebTestClient client;

    @MockBean
    private ReactiveOpaqueTokenIntrospector introspector;

    @BeforeEach
    void setUp() {
        reset(introspector);
        OAuth2AuthenticatedPrincipal principal = new DefaultOAuth2AuthenticatedPrincipal(
                "alice", Map.of("username", "alice", "tenant_id", 1L),
                List.of(new SimpleGrantedAuthority("SCOPE_bixi")));
        when(introspector.introspect("valid-token")).thenReturn(Mono.just(principal));
        when(introspector.introspect("expired-token"))
                .thenReturn(Mono.error(new BadOpaqueTokenException("expired")));
        when(introspector.introspect("down-token"))
                .thenReturn(Mono.error(new OAuth2IntrospectionException("auth unavailable")));
    }

    @Test
    void healthProbeRemainsPublic() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void routedServiceHealthProbesRemainPublic() {
        for (String path : List.of(
                "/admin/actuator/health", "/admin/actuator/health/readiness",
                "/gen/actuator/health", "/gen/actuator/health/liveness",
                "/job/actuator/health", "/job/actuator/health/liveness",
                "/admin/workflow/actuator/health", "/admin/workflow/actuator/health/readiness",
                "/ai/actuator/health", "/ai/actuator/health/liveness")) {
            client.get().uri(path).exchange().expectStatus().isOk();
        }
    }

    @Test
    void routedServiceOperationalEndpointsRemainProtected() {
        for (String path : List.of(
                "/admin/actuator/env", "/gen/actuator/env",
                "/job/actuator/env",
                "/admin/workflow/actuator/env", "/ai/actuator/env")) {
            client.get().uri(path).exchange().expectStatus().isUnauthorized();
        }
    }

    @Test
    void oauthLoginAndTokenIssuanceRemainPublic() {
        client.post().uri("/auth/oauth2/token").exchange().expectStatus().isOk();
        client.get().uri("/auth/token/login").exchange().expectStatus().isOk();
    }

    @Test
    void browserLogoutReachesAuthWithoutBearerForDownstreamCsrfCheck() {
        client.post().uri("/auth/logout").exchange().expectStatus().isOk();
    }

    @Test
    void noticeProviderReceiptReachesUpmsWithoutBearerToken() {
        client.post().uri("/admin/notice/delivery/receipt")
                .bodyValue("{}").exchange().expectStatus().isOk();
    }

    @Test
    void businessRoutesRequireBearerAuthentication() {
        client.get().uri("/admin/demo").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void documentationAndOperationalDataRequireBearerAuthentication() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isUnauthorized();
        client.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void validBearerTokenReachesBusinessRoute() {
        client.get().uri("/admin/demo")
                .headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("alice");
    }

    @Test
    void invalidBearerTokenIsRejectedBeforeBusinessHandler() {
        client.get().uri("/admin/demo")
                .headers(headers -> headers.setBearerAuth("expired-token"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void authDependencyFailureFailsClosedWithRetryableStatus() {
        client.get().uri("/admin/demo")
                .headers(headers -> headers.setBearerAuth("down-token"))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "5");
    }

    @RestController
    static class Endpoint {
        @GetMapping({
                "/actuator/health", "/admin/actuator/health", "/admin/actuator/health/readiness",
                "/gen/actuator/health", "/gen/actuator/health/liveness",
                "/job/actuator/health", "/job/actuator/health/liveness",
                "/admin/workflow/actuator/health", "/admin/workflow/actuator/health/readiness",
                "/ai/actuator/health", "/ai/actuator/health/liveness",
                "/auth/oauth2/token", "/auth/token/login"})
        String publicEndpoint() {
            return "ok";
        }

        @org.springframework.web.bind.annotation.PostMapping("/auth/oauth2/token")
        String tokenEndpoint() {
            return "ok";
        }

        @org.springframework.web.bind.annotation.PostMapping("/auth/logout")
        String logoutEndpoint() {
            return "ok";
        }

        @org.springframework.web.bind.annotation.PostMapping("/admin/notice/delivery/receipt")
        String noticeReceiptEndpoint() {
            return "ok";
        }

        @GetMapping({"/admin/demo", "/v3/api-docs", "/actuator/env", "/job/actuator/env"})
        String protectedEndpoint(org.springframework.security.core.Authentication authentication) {
            return authentication.getName();
        }
    }
}
