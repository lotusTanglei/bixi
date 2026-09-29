package com.lotus.bixi.gateway.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Security boundary for the cloud-only Gateway.
 *
 * <p>The downstream services still perform their own authorization checks. The
 * Gateway authenticates the bearer token first so an unauthenticated request
 * cannot reach a business route or an operational endpoint.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveOpaqueTokenIntrospector.class)
    ReactiveOpaqueTokenIntrospector gatewayOpaqueTokenIntrospector(
            GatewaySecurityProperties properties,
            ObjectProvider<WebClient.Builder> webClientBuilderProvider) {
        WebClient.Builder builder = webClientBuilderProvider.getIfAvailable(WebClient::builder);
        return new GatewayReactiveOpaqueTokenIntrospector(builder, properties);
    }

    @Bean
    SecurityWebFilterChain gatewaySecurityFilterChain(
            ServerHttpSecurity http,
            ReactiveOpaqueTokenIntrospector introspector) {
        ServerAuthenticationEntryPoint entryPoint = (exchange, exception) -> {
            var response = exchange.getResponse();
            if (exception instanceof AuthenticationServiceException) {
                response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                response.getHeaders().set(HttpHeaders.RETRY_AFTER, "5");
            } else {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
            }
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            return response.setComplete();
        };
        ServerAuthenticationEntryPointFailureHandler failureHandler =
                new ServerAuthenticationEntryPointFailureHandler(entryPoint);
        failureHandler.setRethrowAuthenticationServiceException(false);

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Gateway and routed service health probes are used by Compose and the load balancer.
                        .pathMatchers("/actuator/health", "/actuator/health/**",
                                "/auth/actuator/health", "/auth/actuator/health/**",
                                "/admin/actuator/health", "/admin/actuator/health/**",
                                "/gen/actuator/health", "/gen/actuator/health/**",
                                "/job/actuator/health", "/job/actuator/health/**",
                                "/admin/workflow/actuator/health", "/admin/workflow/actuator/health/**",
                                "/ai/actuator/health", "/ai/actuator/health/**", "/error")
                        .permitAll()
                        // OAuth endpoints required to obtain or start a login flow.
                        .pathMatchers("/auth/oauth2/token", "/auth/oauth2/authorize", "/auth/oauth2/jwks",
                                "/auth/.well-known/**", "/auth/token/login", "/auth/token/form",
                                "/auth/token/confirm_access", "/auth/code/**", "/auth/css/**",
                                "/auth/error")
                        .permitAll()
                        // Provider callbacks are authenticated by the UPMS HMAC
                        // contract after the existing /admin route strips its prefix.
                        .pathMatchers(HttpMethod.POST, "/admin/notice/delivery/receipt")
                        .permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .authenticationFailureHandler(failureHandler)
                        .opaqueToken(opaque -> opaque.introspector(introspector)))
                .build();
    }
}
