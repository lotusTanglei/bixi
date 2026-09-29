package com.lotus.bixi.monitor.config;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The monitor exposes only the health probe anonymously; all operational data
 * remains behind the monitor login/basic-auth boundary.
 */
class SecuritySecureConfigTest {

    @Test
    void rejectsMissingMonitorCredentials() {
        var properties = new SecurityProperties();
        properties.getUser().setName("");
        properties.getUser().setPassword("");
        var config = new SecuritySecureConfig(new AdminServerProperties(), properties);

        assertThatThrownBy(() -> config.userDetailsService(config.passwordEncoder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Monitor credentials must be configured");
    }

    @Test
    void operationalActuatorEndpointsRequireAuthentication() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestConfiguration.class, TestConfiguration.Endpoints.class, SecuritySecureConfig.class);
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                    .build();

            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ok"));
            mvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ok"));
            mvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ok"));
            mvc.perform(get("/actuator/info").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/actuator/metrics").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/actuator/env").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            mvc.perform(get("/actuator/info").header("Authorization", basic("monitor", "secret")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("info"));
        }
    }

    private static String basic(String username, String password) {
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    static class TestConfiguration {

        @Bean
        AdminServerProperties adminServerProperties() {
            return new AdminServerProperties();
        }

        @Bean
        SecurityProperties securityProperties() {
            SecurityProperties properties = new SecurityProperties();
            properties.getUser().setName("monitor");
            properties.getUser().setPassword("secret");
            return properties;
        }

        @RestController
        static class Endpoints {

            @GetMapping({"/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"})
            String health() {
                return "ok";
            }

            @GetMapping("/actuator/info")
            String info() {
                return "info";
            }

            @GetMapping({"/actuator/metrics", "/actuator/env"})
            String operational() {
                return "operational";
            }
        }
    }
}
