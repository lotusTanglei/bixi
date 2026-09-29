package com.lotus.bixi.gateway.security;

import com.lotus.bixi.common.core.constant.SecurityConstants;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates Bixi's reference access token through the internal Auth endpoint.
 *
 * <p>Bixi intentionally issues opaque reference tokens. The Auth endpoint
 * returns the same token response shape used by the browser login flow rather
 * than RFC 7662's {@code active} document, so this adapter validates and maps
 * that response without ever putting the submitted token in an exception or
 * log message.</p>
 */
public final class GatewayReactiveOpaqueTokenIntrospector implements ReactiveOpaqueTokenIntrospector {

    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final URI introspectionUri;
    private final Duration requestTimeout;

    public GatewayReactiveOpaqueTokenIntrospector(WebClient.Builder webClientBuilder,
                                                  GatewaySecurityProperties properties) {
        this.webClient = webClientBuilder.build();
        this.introspectionUri = URI.create(properties.getIntrospectionUri());
        this.requestTimeout = properties.getRequestTimeout();
    }

    @Override
    public Mono<OAuth2AuthenticatedPrincipal> introspect(String token) {
        if (!StringUtils.hasText(token)) {
            return Mono.error(invalidToken());
        }

        URI requestUri = UriComponentsBuilder.fromUri(this.introspectionUri)
                .queryParam("token", token)
                .build()
                .encode()
                .toUri();

        return this.webClient.get()
                .uri(requestUri)
                .header(SecurityConstants.FROM, SecurityConstants.FROM_IN)
                .retrieve()
                .onStatus(status -> status.isError(), response -> Mono.error(
                        response.statusCode().is4xxClientError()
                                ? new BadOpaqueTokenException("Invalid bearer token")
                                : new OAuth2IntrospectionException("Token introspection endpoint unavailable")))
                .bodyToMono(RESPONSE_TYPE)
                .timeout(this.requestTimeout)
                .switchIfEmpty(Mono.error(invalidToken()))
                .map(body -> toPrincipal(token, body))
                .onErrorMap(error -> error instanceof OAuth2IntrospectionException
                        ? error
                        : new OAuth2IntrospectionException("Token introspection failed", error));
    }

    private OAuth2AuthenticatedPrincipal toPrincipal(String token, Map<String, Object> body) {
        Object returnedToken = body.get("access_token");
        if (!(returnedToken instanceof String) || !token.equals(returnedToken)) {
            throw invalidToken();
        }

        Object expiresIn = body.get("expires_in");
        if (expiresIn instanceof Number number && number.longValue() <= 0) {
            throw invalidToken();
        }

        String name = principalName(body);
        if (!StringUtils.hasText(name)) {
            throw invalidToken();
        }

        Map<String, Object> attributes = new LinkedHashMap<>(body);
        // Never copy credentials into the SecurityContext principal.
        attributes.remove("access_token");
        attributes.remove("refresh_token");
        normalizeTemporalClaim(attributes, OAuth2TokenIntrospectionClaimNames.IAT);
        normalizeTemporalClaim(attributes, OAuth2TokenIntrospectionClaimNames.EXP);
        normalizeTemporalClaim(attributes, OAuth2TokenIntrospectionClaimNames.NBF);
        attributes.put("active", true);

        return new DefaultOAuth2AuthenticatedPrincipal(name, attributes, authorities(body));
    }

    private static void normalizeTemporalClaim(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        if (value == null || value instanceof Instant) {
            return;
        }
        if (!(value instanceof Number number)) {
            throw invalidToken();
        }
        try {
            BigDecimal seconds = new BigDecimal(number.toString());
            BigDecimal wholeSeconds = seconds.setScale(0, RoundingMode.FLOOR);
            long epochSecond = wholeSeconds.longValueExact();
            int nanos = seconds.subtract(wholeSeconds)
                    .movePointRight(9)
                    .setScale(0, RoundingMode.DOWN)
                    .intValueExact();
            attributes.put(name, Instant.ofEpochSecond(epochSecond, nanos));
        } catch (ArithmeticException | DateTimeException | NumberFormatException ex) {
            throw invalidToken();
        }
    }

    private static String principalName(Map<String, Object> body) {
        String direct = firstText(body.get("username"), body.get("user_name"), body.get("sub"),
                body.get("client_id"));
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        Object userInfo = body.get("user_info");
        if (userInfo instanceof Map<?, ?> map) {
            return firstText(map.get("username"), map.get("user_name"), map.get("id"));
        }
        return null;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static Collection<GrantedAuthority> authorities(Map<String, Object> body) {
        Object scopes = body.get("scope");
        if (scopes == null) {
            return Collections.emptyList();
        }
        List<GrantedAuthority> result = new ArrayList<>();
        if (scopes instanceof Collection<?> collection) {
            collection.forEach(scope -> addScope(result, scope));
        } else {
            for (String scope : String.valueOf(scopes).split("[ ,]+")) {
                addScope(result, scope);
            }
        }
        return result;
    }

    private static void addScope(List<GrantedAuthority> authorities, Object scope) {
        if (scope != null && StringUtils.hasText(String.valueOf(scope))) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }
    }

    private static OAuth2IntrospectionException invalidToken() {
        return new BadOpaqueTokenException("Invalid bearer token");
    }
}
