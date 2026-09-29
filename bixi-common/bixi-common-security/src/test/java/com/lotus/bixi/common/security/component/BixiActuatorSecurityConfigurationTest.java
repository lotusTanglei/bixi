package com.lotus.bixi.common.security.component;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BixiActuatorSecurityConfigurationTest {

    @Test
    void protectsOperationalActuatorEndpointsWithTheSbaCredentials() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            TestPropertyValues.of(
                    "spring.boot.admin.client.enabled=true",
                    "bixi.sba.actuator.username=monitor",
                    "bixi.sba.actuator.password=secret")
                    .applyTo(context);
            context.setServletContext(new MockServletContext());
            context.register(TestConfiguration.class, TestConfiguration.Endpoints.class,
                    BixiActuatorSecurityConfiguration.class);
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                    .build();

            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ok"));
            mvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ready"));
            mvc.perform(get("/actuator/info").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/actuator/loggers").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/actuator/logfile"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/actuator/metrics").header("Authorization", basic("monitor", "wrong")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/actuator/metrics").header("Authorization", basic("monitor", "secret")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("metrics"));
            mvc.perform(get("/actuator/loggers").header("Authorization", basic("monitor", "secret")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("loggers"));
            mvc.perform(get("/actuator/logfile").header("Authorization", basic("monitor", "secret")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("logfile"));
        }
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    static class TestConfiguration {

        @RestController
        static class Endpoints {

            @GetMapping("/actuator/health")
            String health() {
                return "ok";
            }

            @GetMapping("/actuator/health/readiness")
            String readiness() {
                return "ready";
            }

            @GetMapping("/actuator/info")
            String info() {
                return "info";
            }

            @GetMapping("/actuator/metrics")
            String metrics() {
                return "metrics";
            }

            @GetMapping("/actuator/loggers")
            String loggers() {
                return "loggers";
            }

            @GetMapping("/actuator/logfile")
            String logfile() {
                return "logfile";
            }
        }
    }
}
