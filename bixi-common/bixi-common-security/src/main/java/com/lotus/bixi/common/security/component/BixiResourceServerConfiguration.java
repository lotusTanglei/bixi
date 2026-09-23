

package com.lotus.bixi.common.security.component;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import com.lotus.bixi.common.security.filter.EncryptionFilter;

/**
 * @author 唐磊
 * @date 2025-01-01
 * <p>
 * 资源服务器认证授权配置
 */
@Slf4j
@EnableWebSecurity
@EnableMethodSecurity
public class BixiResourceServerConfiguration {

    protected final ResourceAuthExceptionEntryPoint resourceAuthExceptionEntryPoint;

    private final PermitAllUrlProperties permitAllUrl;

    private final BixiBearerTokenExtractor bixiBearerTokenExtractor;

    private final OpaqueTokenIntrospector customOpaqueTokenIntrospector;

    private final ObjectProvider<EncryptionFilter> encryptionFilterProvider;

    public BixiResourceServerConfiguration(ResourceAuthExceptionEntryPoint resourceAuthExceptionEntryPoint,
                                           PermitAllUrlProperties permitAllUrl,
                                           BixiBearerTokenExtractor bixiBearerTokenExtractor,
                                           OpaqueTokenIntrospector customOpaqueTokenIntrospector,
                                           ObjectProvider<EncryptionFilter> encryptionFilterProvider) {
        this.resourceAuthExceptionEntryPoint = resourceAuthExceptionEntryPoint;
        this.permitAllUrl = permitAllUrl;
        this.bixiBearerTokenExtractor = bixiBearerTokenExtractor;
        this.customOpaqueTokenIntrospector = customOpaqueTokenIntrospector;
        this.encryptionFilterProvider = encryptionFilterProvider;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AntPathRequestMatcher[] requestMatchers = permitAllUrl.getUrls()
                .stream()
                .map(AntPathRequestMatcher::new)
                .toList()
                .toArray(new AntPathRequestMatcher[]{});

        http.authorizeHttpRequests(authorizeRequests -> authorizeRequests.dispatcherTypeMatchers(DispatcherType.ASYNC)
                        .permitAll()
                        .requestMatchers(requestMatchers)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                // SSO form sessions belong to the auth chains; business APIs require a bearer token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(
                        oauth2 -> oauth2.opaqueToken(token -> token.introspector(customOpaqueTokenIntrospector))
                                .authenticationEntryPoint(resourceAuthExceptionEntryPoint)
                                .bearerTokenResolver(bixiBearerTokenExtractor)
                                .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                                    @Override
                                    public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                        var failureHandler = new AuthenticationEntryPointFailureHandler(resourceAuthExceptionEntryPoint);
                                        failureHandler.setRethrowAuthenticationServiceException(false);
                                        filter.setAuthenticationFailureHandler(failureHandler);
                                        return filter;
                                    }
                                }))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class);

        EncryptionFilter encryptionFilter = encryptionFilterProvider.getIfAvailable();
        if (encryptionFilter != null) {
            http.addFilterAfter(encryptionFilter, TenantContextFilter.class);
        }

        return http.build();
    }

}
