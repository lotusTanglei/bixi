package com.lotus.bixi.auth.security;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.auth.support.filter.*;
import com.lotus.bixi.auth.endpoint.BixiTokenEndpoint;
import com.lotus.bixi.upms.api.service.ClientDetailsQueryService;
import com.lotus.bixi.upms.api.service.TokenManagementService;
import com.lotus.bixi.upms.api.entity.SysOauthClientDetails;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.common.log.config.BixiLogProperties;
import com.lotus.bixi.common.security.component.*;
import com.lotus.bixi.common.security.service.*;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockServletContext;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;
import org.springframework.web.servlet.view.freemarker.FreeMarkerViewResolver;

import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationSecurityChainTest {
    @ParameterizedTest(name = "form login, resource server = {0}")
    @ValueSource(booleans = {false, true})
    void scannedConfigurationHandlesFormLogin(boolean single) throws Exception {
        try (var context = context(single)) {
            MockMvc mvc = mvc(context);
            var success = mvc.perform(formRequest(loginPage(mvc, ""), "").param("username", "alice")
                    .param("password", "correct-password"))
                    .andExpect(status().is3xxRedirection()).andReturn();
            assertThat(success.getResponse().getRedirectedUrl()).isEqualTo("/");
            for (String grant : List.of("", "mobile", "client_credentials", "refresh_token")) {
                mvc.perform(formRequest(loginPage(mvc, ""), "").param("username", "alice")
                                .param("password", "wrong").param("grant_type", grant))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrlPattern("login?error=*"));
            }
        }
    }

    @ParameterizedTest(name = "OAuth password login, resource server = {0}")
    @ValueSource(booleans = {false, true})
    void oauthPasswordLoginChecksCredentials(boolean single) throws Exception {
        try (var context = context(single)) {
            MockMvc mvc = mvc(context);
            mvc.perform(post("/oauth2/token").servletPath("/oauth2/token")
                            .header("Authorization", basic()).param("grant_type", "password")
                            .param("username", "alice").param("password", "correct-password"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isNotEmpty());
            mvc.perform(post("/oauth2/token").servletPath("/oauth2/token")
                            .header("Authorization", basic()).param("grant_type", "password")
                            .param("username", "alice").param("password", "wrong"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.access_token").doesNotExist());
            if (single) {
                mvc.perform(get("/business").header("Authorization", "Bearer resource-token"))
                        .andExpect(status().isOk()).andExpect(content().string("alice"));
            }
        }
    }

    @ParameterizedTest(name = "ignored client still verifies SMS, resource server = {0}")
    @ValueSource(booleans = {false, true})
    void ignoredClientCannotBypassSmsProof(boolean single) throws Exception {
        try (var context = context(single)) {
            MockMvc mvc = mvc(context);
            mvc.perform(post("/oauth2/token").servletPath("/oauth2/token")
                            .header("Authorization", basic()).param("grant_type", "mobile")
                            .param("mobile", "13800138000"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.access_token").doesNotExist());
            mvc.perform(post("/oauth2/token").servletPath("/oauth2/token")
                            .header("Authorization", basic()).param("grant_type", "mobile")
                            .param("mobile", "13800138000").param("code", "wrong"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.access_token").doesNotExist());
            // A non-exempt client uses the same mandatory SMS verifier; image captcha must not consume its code.
            context.getBean(AuthSecurityConfigProperties.class).setIgnoreClients(List.of());
            RedisTemplate<String, Object> redis = context.getBean(RedisTemplate.class);
            when(redis.opsForValue().getAndDelete("SMS_CODE_KEY:13800138000")).thenReturn("123456", null);
            mvc.perform(post("/oauth2/token").servletPath("/oauth2/token")
                            .header("Authorization", basic()).param("grant_type", "mobile")
                            .param("mobile", "13800138000").param("code", "123456"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isNotEmpty());
            mvc.perform(post("/oauth2/token").servletPath("/oauth2/token")
                            .header("Authorization", basic()).param("grant_type", "mobile")
                            .param("mobile", "13800138000").param("code", "123456"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.access_token").doesNotExist());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void formLoginRequiresCsrf(boolean single) throws Exception {
        try (var context = context(single)) {
            MockMvc mvc = mvc(context);
            var page = loginPage(mvc, "");
            mvc.perform(post("/token/form").session(page.session()).param("username", "alice").param("password", "correct-password"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/token/form").session(page.session()).param("username", "alice").param("password", "correct-password")
                            .param("_csrf", "invalid"))
                    .andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/admin"})
    void loginTemplateSuppliesCsrfAndRelativeAction(String prefix) throws Exception {
        try (var context = context(!prefix.isEmpty())) {
            var result = mvc(context).perform(get(prefix + "/token/login").contextPath(prefix)
                            .servletPath("/token/login").param("error", "<script>alert(1)</script>"))
                    .andExpect(status().isOk()).andReturn();
            String html = result.getResponse().getContentAsString();
            assertThat(html).contains("action=\"form\"").contains("&lt;script&gt;");
            assertThat(html).contains("name=\"_csrf\"");
            assertThat(result.getRequest().getSession(false)).isNotNull();
        }
    }

    @Test
    void formSessionDoesNotAuthenticateBearerOnlyBusinessEndpoints() throws Exception {
        try (var context = context(true)) {
            MockMvc mvc = mvc(context);
            var login = mvc.perform(formRequest(loginPage(mvc, ""), "").param("username", "alice")
                    .param("password", "correct-password")).andExpect(status().is3xxRedirection()).andReturn();
            var session = (MockHttpSession) login.getRequest().getSession(false);
            assertThat(session).isNotNull();
            mvc.perform(get("/business").session(session)).andExpect(status().isFailedDependency());
            mvc.perform(post("/business").session(session)).andExpect(status().isFailedDependency());
            mvc.perform(post("/business").session(session).header("Authorization", "Bearer resource-token"))
                    .andExpect(status().isOk()).andExpect(content().string("alice"));
        }
    }

    @Test
    void getLogoutDoesNotInvalidateFormSession() throws Exception {
        try (var context = context(false)) {
            MockMvc mvc = mvc(context);
            var login = mvc.perform(formRequest(loginPage(mvc, ""), "").param("username", "alice")
                    .param("password", "correct-password")).andReturn();
            var session = (MockHttpSession) login.getRequest().getSession(false);
            mvc.perform(get("/logout").session(session));
            assertThat(session.isInvalid()).isFalse();
            mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
            assertThat(session.isInvalid()).isFalse();
            var page = loginPage(mvc, "", session);
            mvc.perform(post("/logout").session(session).param("_csrf", page.token()))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("token/login"));
            assertThat(session.isInvalid()).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/admin"})
    void consentTemplatePreservesContextAndProxyPrefixes(String prefix) throws Exception {
        try (var context = context(!prefix.isEmpty())) {
            MockMvc mvc = mvc(context);
            var login = mvc.perform(formRequest(loginPage(mvc, prefix), prefix)
                    .param("username", "alice").param("password", "correct-password")).andReturn();
            var session = (MockHttpSession) login.getRequest().getSession(false);
            mvc.perform(get(prefix + "/token/confirm_access").contextPath(prefix).servletPath("/token/confirm_access")
                            .session(session).param("client_id", "ignored-client").param("scope", "server").param("state", "state"))
                    .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"../oauth2/authorize\"")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/admin"})
    void browserAuthorizationSurvivesLoginSavedRequestAndConsent(String contextPath) throws Exception {
        try (var context = context(!contextPath.isEmpty())) {
            MockMvc mvc = mvc(context);
            var authorize = mvc.perform(get(contextPath + "/oauth2/authorize").contextPath(contextPath)
                            .servletPath("/oauth2/authorize").accept("text/html").queryParam("response_type", "code")
                            .queryParam("client_id", "ignored-client").queryParam("redirect_uri", "https://client.test/callback")
                            .queryParam("scope", "server").queryParam("state", "browser-state"))
                    .andExpect(status().is3xxRedirection()).andReturn();
            assertThat(java.net.URI.create(authorize.getResponse().getRedirectedUrl()).getPath())
                    .isEqualTo(contextPath + "/token/login");
            var session = (MockHttpSession) authorize.getRequest().getSession(false);
            var page = loginPage(mvc, contextPath, session);
            var login = mvc.perform(formRequest(page, contextPath).param("username", "alice")
                            .param("password", "correct-password"))
                    .andExpect(status().is3xxRedirection()).andReturn();
            var savedRequest = java.net.URI.create(login.getResponse().getRedirectedUrl());
            assertThat(savedRequest.getPath()).isEqualTo(contextPath + "/oauth2/authorize");
            var consent = mvc.perform(get(savedRequest).contextPath(contextPath).servletPath("/oauth2/authorize")
                            .accept("text/html").session(session))
                    .andExpect(status().is3xxRedirection()).andReturn();
            var consentUri = java.net.URI.create(consent.getResponse().getRedirectedUrl());
            assertThat(consentUri.getPath()).isEqualTo(contextPath + "/token/confirm_access");
            String html = mvc.perform(get(consentUri).contextPath(contextPath).servletPath("/token/confirm_access")
                            .session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var consentState = java.util.regex.Pattern.compile("name=\"state\" value=\"([^\"]+)\"").matcher(html);
            assertThat(consentState.find()).isTrue();
            assertThat(html).contains("action=\"../oauth2/authorize\"");
            // The OAuth authorization endpoint uses its bound state and remains exempt from form CSRF.
            var callback = mvc.perform(post(contextPath + "/oauth2/authorize").contextPath(contextPath)
                            .servletPath("/oauth2/authorize").session(session)
                            .contentType("application/x-www-form-urlencoded")
                            .param("client_id", "ignored-client").param("state", consentState.group(1)).param("scope", "server"))
                    .andExpect(status().is3xxRedirection()).andReturn();
            var callbackUri = java.net.URI.create(callback.getResponse().getRedirectedUrl());
            assertThat(callbackUri.getHost()).isEqualTo("client.test");
            assertThat(callbackUri.getQuery()).contains("code=", "state=browser-state");
        }
    }

    private record LoginPage(MockHttpSession session, String token) { }

    private LoginPage loginPage(MockMvc mvc, String prefix) throws Exception {
        return loginPage(mvc, prefix, null);
    }

    private LoginPage loginPage(MockMvc mvc, String prefix, MockHttpSession session) throws Exception {
        var request = get(prefix + "/token/login").contextPath(prefix).servletPath("/token/login");
        if (session != null) request.session(session);
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var csrf = java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"")
                .matcher(result.getResponse().getContentAsString());
        assertThat(csrf.find()).as("real login template renders a CSRF token").isTrue();
        return new LoginPage((MockHttpSession) result.getRequest().getSession(false), csrf.group(1));
    }

    private MockHttpServletRequestBuilder formRequest(LoginPage page, String prefix) {
        return post(prefix + "/token/form").contextPath(prefix).servletPath("/token/form")
                .session(page.session()).param("_csrf", page.token());
    }

    private AnnotationConfigWebApplicationContext context(boolean single) {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Fixtures.class);
        // Real component scanning must discover the form configuration; @Import would hide missing @Configuration.
        context.scan("com.lotus.bixi.auth.config");
        if (single) context.register(BixiResourceServerConfiguration.class);
        context.refresh();
        return context;
    }

    private MockMvc mvc(AnnotationConfigWebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new RequestContextFilter(), context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    private String basic() {
        return "Basic " + Base64.getEncoder().encodeToString("ignored-client:secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    static class Fixtures {
        @Bean static SpringUtil springUtil() { return new SpringUtil(); }
        @Bean SpringContextHolder springContextHolder() { return new SpringContextHolder(); }
        @Bean StaticMessageSource securityMessageSource() {
            var source = new StaticMessageSource();
            source.setUseCodeAsDefaultMessage(true);
            return source;
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean BixiLogProperties logProperties() {
            var properties = new BixiLogProperties();
            properties.setExcludeFields(List.of("password", "code"));
            return properties;
        }
        @Bean BixiUserDetailsService users() {
            return name -> new BixiUser(1L, 1L, name, "{noop}correct-password", "13800138000", true,
                    true, true, true, AuthorityUtils.createAuthorityList("demo_task_view"));
        }
        @Bean AuthSecurityConfigProperties authProperties() {
            var properties = new AuthSecurityConfigProperties();
            properties.setIgnoreClients(List.of("ignored-client"));
            return properties;
        }
        @Bean PasswordDecoderFilter passwordDecoderFilter(AuthSecurityConfigProperties properties) { return new PasswordDecoderFilter(properties); }
        @Bean ValidateCodeFilter validateCodeFilter(AuthSecurityConfigProperties properties) { return new ValidateCodeFilter(properties); }
        @Bean @SuppressWarnings("unchecked") RedisTemplate<String, Object> redisTemplate() {
            RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
            when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
            return redis;
        }
        @Bean OAuth2AuthorizationService authorizationService() { return new InMemoryOAuth2AuthorizationService(); }
        @Bean RegisteredClientRepository clients() {
            return new InMemoryRegisteredClientRepository(RegisteredClient.withId("client-id")
                    .clientId("ignored-client").clientSecret("{noop}secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("https://client.test/callback")
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                    .authorizationGrantType(AuthorizationGrantType.PASSWORD)
                    .authorizationGrantType(new AuthorizationGrantType("mobile"))
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE).build())
                    .scope("server").build());
        }
        @Bean PermitAllUrlProperties permitAllUrlProperties() { return new PermitAllUrlProperties(); }
        @Bean BixiBearerTokenExtractor bearerTokenExtractor(PermitAllUrlProperties urls) { return new BixiBearerTokenExtractor(urls); }
        @Bean ResourceAuthExceptionEntryPoint entryPoint(StaticMessageSource source) { return new ResourceAuthExceptionEntryPoint(new ObjectMapper(), source); }
        @Bean OpaqueTokenIntrospector introspector() {
            return token -> new DefaultOAuth2AuthenticatedPrincipal("alice", Map.of("sub", "alice"),
                    AuthorityUtils.createAuthorityList("demo_task_view"));
        }
        @Bean BusinessEndpoint endpoint() { return new BusinessEndpoint(); }
        @Bean BixiTokenEndpoint tokenEndpoint(OAuth2AuthorizationService authorizationService) {
            ClientDetailsQueryService clients = mock(ClientDetailsQueryService.class);
            var client = new SysOauthClientDetails();
            client.setScope("server");
            when(clients.getClientDetailsById(anyString())).thenReturn(R.ok(client));
            return new BixiTokenEndpoint(authorizationService, clients, mock(TokenManagementService.class));
        }
        @Bean FreeMarkerConfigurer freeMarkerConfigurer() {
            var configurer = new FreeMarkerConfigurer();
            configurer.setTemplateLoaderPath("classpath:/templates/");
            configurer.setDefaultEncoding("UTF-8");
            return configurer;
        }
        @Bean FreeMarkerViewResolver freeMarkerViewResolver() {
            var resolver = new FreeMarkerViewResolver();
            resolver.setSuffix(".ftl");
            resolver.setContentType("text/html;charset=UTF-8");
            return resolver;
        }
    }

    @RestController
    static class BusinessEndpoint {
        @RequestMapping(value = "/business", method = {RequestMethod.GET, RequestMethod.POST}) String business(java.security.Principal principal) { return principal.getName(); }
    }
}
