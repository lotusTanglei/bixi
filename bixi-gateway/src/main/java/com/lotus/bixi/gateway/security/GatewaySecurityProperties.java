package com.lotus.bixi.gateway.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Runtime settings for the Gateway resource-server boundary.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bixi.gateway.security")
public class GatewaySecurityProperties {

    /**
     * Internal Auth endpoint used to validate the reference access token.
     */
    private String introspectionUri = "http://127.0.0.1:3000/token/check_token";

    /**
     * Upper bound for one token validation request.
     */
    private Duration requestTimeout = Duration.ofSeconds(3);
}
