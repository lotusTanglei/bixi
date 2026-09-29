package com.lotus.bixi.common.security.component;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Adds a private Basic Auth chain for Actuator when the SBA client is enabled.
 * The regular bearer-token chain remains responsible for business endpoints.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.boot.admin.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BixiActuatorSecurityProperties.class)
public class BixiActuatorSecurityConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain bixiActuatorSecurityFilterChain(
            HttpSecurity http,
            BixiActuatorSecurityProperties properties) throws Exception {
        if (isBlank(properties.getUsername()) || isBlank(properties.getPassword())) {
            throw new IllegalStateException("SBA Actuator credentials must be configured when the SBA client is enabled");
        }

        var users = new InMemoryUserDetailsManager(User.withUsername(properties.getUsername())
                .password(passwordEncoder().encode(properties.getPassword()))
                .roles("ACTUATOR")
                .build());
        var provider = new DaoAuthenticationProvider(passwordEncoder());
        provider.setUserDetailsService(users);

        http.securityMatcher(new AntPathRequestMatcher("/actuator/**"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .authenticationProvider(provider)
                .httpBasic(httpBasic -> {
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
