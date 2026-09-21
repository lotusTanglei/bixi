package com.lotus.bixi.auth.config;

import com.lotus.bixi.auth.support.core.FormIdentityLoginConfigurer;
import com.lotus.bixi.auth.support.core.BixiDaoAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 服务安全相关配置
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class WebSecurityConfiguration {

    /**
     * spring security 默认的安全策略
     *
     * @param http security注入点
     * @return SecurityFilterChain
     * @throws Exception
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        // Disjoint from the OAuth endpoint chain; both precede the single-mode resource chain.
        http.securityMatcher("/token/login", "/token/form", "/token/confirm_access", "/logout");
        http.authorizeHttpRequests(authorizeRequests -> authorizeRequests.requestMatchers("/token/login", "/token/form")
                .permitAll()
                .anyRequest()
                .authenticated()).headers(header -> header.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)// 避免iframe同源无法登录许iframe
        ).with(new FormIdentityLoginConfigurer(), Customizer.withDefaults()); // 表单登录个性化
        // 处理 UsernamePasswordAuthenticationToken
        http.authenticationProvider(new BixiDaoAuthenticationProvider());
        return http.build();
    }

    /**
     * 暴露静态资源
     * <p>
     * https://github.com/spring-projects/spring-security/issues/10938
     *
     * @param http
     * @return
     * @throws Exception
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain resources(HttpSecurity http) throws Exception {
        http.securityMatchers((matchers) -> matchers.requestMatchers("/actuator/**", "/css/**", "/error", "/code/image"))
                .authorizeHttpRequests((authorize) -> authorize.anyRequest().permitAll())
                .requestCache(RequestCacheConfigurer::disable)
                .securityContext(AbstractHttpConfigurer::disable)
                .sessionManagement(AbstractHttpConfigurer::disable);
        return http.build();
    }

}
