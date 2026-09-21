package com.lotus.bixi.common.security.component;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.security.service.BixiUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import jakarta.servlet.Filter;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the production resource chain and introspector, including Spring's failure handler. */
@WebAppConfiguration
@SpringJUnitConfig(ResourceIntrospectionFailureTest.Config.class)
class ResourceIntrospectionFailureTest {
    @Autowired WebApplicationContext context;
    @Autowired OAuth2AuthorizationService store;
    @Autowired Endpoint endpoint;
    private MockMvc http;

    @BeforeEach
    void setup() {
        reset(store);
        endpoint.calls.set(0);
        http = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test
    void redisFailureReturnsRetryableJsonWithoutLeakingCredentialsOrRunningTheController() {
        when(store.findByToken("submitted-token", OAuth2TokenType.ACCESS_TOKEN))
                .thenThrow(new IllegalStateException("redis-password-and-submitted-token"));
        assertThatCode(() -> http.perform(get("/secured").header("Authorization", "Bearer submitted-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("code").value(1))
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(content().string(not(containsString("submitted-token"))))
                .andExpect(content().string(not(containsString("redis-password")))))
                .doesNotThrowAnyException();
        assertThat(endpoint.calls).hasValue(0);
    }

    @Test
    void invalidTokenKeepsTheExistingAuthenticationExpiryContract() throws Exception {
        http.perform(get("/secured").header("Authorization", "Bearer unknown-token"))
                .andExpect(status().isFailedDependency()).andExpect(jsonPath("code").value(1))
                .andExpect(content().string(not(containsString("unknown-token"))))
                .andExpect(header().doesNotExist("Retry-After"));
        assertThat(endpoint.calls).hasValue(0);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(BixiResourceServerConfiguration.class)
    static class Config {
        @Bean static SpringUtil springUtil() { return new SpringUtil(); }
        @Bean OAuth2AuthorizationService authorizations() { return mock(OAuth2AuthorizationService.class); }
        @Bean BixiUserDetailsService users() { return mock(BixiUserDetailsService.class); }
        @Bean OpaqueTokenIntrospector introspector(OAuth2AuthorizationService authorizations) {
            return new BixiCustomOpaqueTokenIntrospector(authorizations);
        }
        @Bean PermitAllUrlProperties permitAll() { return new PermitAllUrlProperties(); }
        @Bean BixiBearerTokenExtractor extractor(PermitAllUrlProperties urls) { return new BixiBearerTokenExtractor(urls); }
        @Bean ResourceAuthExceptionEntryPoint entryPoint() {
            var messages = new StaticMessageSource();
            messages.setUseCodeAsDefaultMessage(true);
            return new ResourceAuthExceptionEntryPoint(new ObjectMapper(), messages);
        }
        @Bean Endpoint endpoint() { return new Endpoint(); }
    }

    @RestController
    static class Endpoint {
        final AtomicInteger calls = new AtomicInteger();
        @GetMapping("/secured") String secured() {
            calls.incrementAndGet();
            return "ok";
        }
    }
}
