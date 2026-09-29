package com.lotus.bixi.upms.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.api.constant.NoticeChannel;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.service.SysUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NoticeChannelDispatcherTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void configuredWebhookUsesRecipientAndTenantBoundPayload() {
        TenantContextHolder.set(7L);
        NoticeChannelProperties properties = new NoticeChannelProperties();
        properties.getChannels().getWebhook().setEnabled(true);
        properties.getChannels().getWebhook().setEndpoint("https://hooks.example.test/bixi");
        properties.getChannels().getWebhook().setBearerToken("hook-secret");
        RecordingHttpClient http = new RecordingHttpClient(204);
        NoticeChannelSender sender = new WebhookNoticeChannelSender(properties, http, new ObjectMapper());
        SysUserService users = mock(SysUserService.class);
        SysUser recipient = user(22L, 7L);
        when(users.getById(22L)).thenReturn(recipient);

        NoticeChannelResult result = dispatcher(sender, users).dispatch(request(NoticeChannel.WEBHOOK), 22L);

        assertThat(result.delivered()).isTrue();
        assertThat(http.uri).isEqualTo(URI.create("https://hooks.example.test/bixi"));
        assertThat(http.headers).containsEntry("Authorization", "Bearer hook-secret");
        assertThat(http.body).contains("\"tenantId\":7", "\"recipientId\":22", "\"title\":\"hello\"");
    }

    @Test
    void configuredEmailUsesTheUsersEmailAddress() {
        TenantContextHolder.set(7L);
        NoticeChannelProperties properties = new NoticeChannelProperties();
        properties.getChannels().getEmail().setEnabled(true);
        properties.getChannels().getEmail().setEndpoint("https://mail.example.test/send");
        properties.getChannels().getEmail().setFrom("no-reply@example.test");
        properties.getChannels().getEmail().setApiKey("mail-secret");
        RecordingHttpClient http = new RecordingHttpClient(202);
        NoticeChannelSender sender = new EmailNoticeChannelSender(properties, http, new ObjectMapper());
        SysUserService users = mock(SysUserService.class);
        SysUser recipient = user(22L, 7L);
        recipient.setEmail("person@example.test");
        when(users.getById(22L)).thenReturn(recipient);

        NoticeChannelResult result = dispatcher(sender, users).dispatch(request(NoticeChannel.EMAIL), 22L);

        assertThat(result.delivered()).isTrue();
        assertThat(http.headers).containsEntry("X-Api-Key", "mail-secret");
        assertThat(http.body).contains("person@example.test", "no-reply@example.test", "hello");
    }

    @Test
    void disabledSmsAndUnconfiguredWechatFailClosed() {
        NoticeChannelProperties properties = new NoticeChannelProperties();
        NoticeChannelSender sms = new SmsNoticeChannelSender(properties);
        NoticeChannelSender wechat = new WechatNoticeChannelSender(properties);

        assertThat(sms.send(request(NoticeChannel.SMS)).delivered()).isFalse();
        assertThat(sms.send(request(NoticeChannel.SMS)).code()).isEqualTo("DISABLED");
        assertThat(wechat.send(request(NoticeChannel.WECHAT)).delivered()).isFalse();
        assertThat(wechat.send(request(NoticeChannel.WECHAT)).code()).isEqualTo("DISABLED");

        properties.getChannels().getSms().setEnabled(true);
        assertThat(sms.send(request(NoticeChannel.SMS)).code()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void disabledSmsStatusWinsEvenWhenTheRecipientHasNoPhone() {
        TenantContextHolder.set(7L);
        NoticeChannelProperties properties = new NoticeChannelProperties();
        SysUserService users = mock(SysUserService.class);
        when(users.getById(22L)).thenReturn(user(22L, 7L));
        NoticeChannelSender sms = new SmsNoticeChannelSender(properties);

        NoticeChannelResult result = dispatcher(sms, users).dispatch(request(NoticeChannel.SMS), 22L);

        assertThat(result.delivered()).isFalse();
        assertThat(result.code()).isEqualTo("DISABLED");
    }

    @Test
    void nonSuccessfulProviderResponseIsRetainedAsAChannelFailure() {
        NoticeChannelProperties properties = new NoticeChannelProperties();
        properties.getChannels().getWebhook().setEnabled(true);
        properties.getChannels().getWebhook().setEndpoint("https://hooks.example.test/bixi");
        RecordingHttpClient http = new RecordingHttpClient(503);
        NoticeChannelSender sender = new WebhookNoticeChannelSender(properties, http, new ObjectMapper());

        NoticeChannelResult result = sender.send(request(NoticeChannel.WEBHOOK));

        assertThat(result.delivered()).isFalse();
        assertThat(result.code()).isEqualTo("HTTP_503");
    }

    @Test
    void webhookAdapterCanUseTheJdkHttpClientAgainstAConfiguredEndpoint() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            NoticeChannelProperties properties = new NoticeChannelProperties();
            properties.getChannels().getWebhook().setEnabled(true);
            properties.getChannels().getWebhook().setEndpoint(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
            NoticeChannelSender sender = new WebhookNoticeChannelSender(properties,
                    new JdkNoticeHttpClient(), new ObjectMapper());

            NoticeChannelResult result = sender.send(request(NoticeChannel.WEBHOOK));

            assertThat(result.delivered()).isTrue();
            assertThat(received.get()).contains("\"noticeId\":99", "\"content\":\"body\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void emailAdapterCanUseTheJdkHttpClientAgainstAConfiguredEndpoint() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mail", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();
        try {
            NoticeChannelProperties properties = new NoticeChannelProperties();
            properties.getChannels().getEmail().setEnabled(true);
            properties.getChannels().getEmail().setEndpoint(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/mail");
            properties.getChannels().getEmail().setFrom("no-reply@example.test");
            NoticeChannelSender sender = new EmailNoticeChannelSender(properties,
                    new JdkNoticeHttpClient(), new ObjectMapper());

            NoticeChannelResult result = sender.send(new NoticeChannelRequest(
                    NoticeChannel.EMAIL, 7L, 99L, 22L, "hello", "body", "person@example.test"));

            assertThat(result.delivered()).isTrue();
            assertThat(received.get()).contains("person@example.test", "no-reply@example.test", "hello");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void smsAdapterCanUseTheJdkHttpClientAndRequiresThePhoneAddress() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sms", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();
        try {
            NoticeChannelProperties properties = new NoticeChannelProperties();
            properties.getChannels().getSms().setEnabled(true);
            properties.getChannels().getSms().setEndpoint(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/sms");
            NoticeChannelSender sender = new SmsNoticeChannelSender(properties,
                    new JdkNoticeHttpClient(), new ObjectMapper());
            SysUserService users = mock(SysUserService.class);
            SysUser recipient = user(22L, 7L);
            recipient.setPhone("13800138000");
            when(users.getById(22L)).thenReturn(recipient);

            TenantContextHolder.set(7L);
            NoticeChannelResult result = dispatcher(sender, users)
                    .dispatch(request(NoticeChannel.SMS), 22L);

            assertThat(result.delivered()).isTrue();
            assertThat(received.get()).contains("13800138000", "SMS", "body");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void wechatAdapterCanUseTheJdkHttpClientAndUsesTheOpenId() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/wechat", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            NoticeChannelProperties properties = new NoticeChannelProperties();
            properties.getChannels().getWechat().setEnabled(true);
            properties.getChannels().getWechat().setEndpoint(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/wechat");
            NoticeChannelSender sender = new WechatNoticeChannelSender(properties,
                    new JdkNoticeHttpClient(), new ObjectMapper());
            SysUserService users = mock(SysUserService.class);
            SysUser recipient = user(22L, 7L);
            recipient.setWxOpenid("openid-22");
            when(users.getById(22L)).thenReturn(recipient);

            TenantContextHolder.set(7L);
            NoticeChannelResult result = dispatcher(sender, users)
                    .dispatch(request(NoticeChannel.WECHAT), 22L);

            assertThat(result.delivered()).isTrue();
            assertThat(received.get()).contains("openid-22", "WECHAT", "body");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void externalChannelsRejectMissingRecipientAddressesBeforeCallingProvider() {
        TenantContextHolder.set(7L);
        NoticeChannelProperties properties = new NoticeChannelProperties();
        properties.getChannels().getSms().setEnabled(true);
        properties.getChannels().getSms().setEndpoint("https://sms.example.test/send");
        RecordingHttpClient http = new RecordingHttpClient(202);
        NoticeChannelSender sender = new SmsNoticeChannelSender(properties, http, new ObjectMapper());
        SysUserService users = mock(SysUserService.class);
        when(users.getById(22L)).thenReturn(user(22L, 7L));

        NoticeChannelResult result = dispatcher(sender, users).dispatch(request(NoticeChannel.SMS), 22L);

        assertThat(result.delivered()).isFalse();
        assertThat(result.code()).isEqualTo("RECIPIENT_ADDRESS_MISSING");
        assertThat(http.uri).isNull();
    }

    @Test
    void tenantMismatchIsRejectedBeforeExternalSend() {
        TenantContextHolder.set(7L);
        NoticeChannelProperties properties = new NoticeChannelProperties();
        properties.getChannels().getWebhook().setEnabled(true);
        properties.getChannels().getWebhook().setEndpoint("https://hooks.example.test/bixi");
        RecordingHttpClient http = new RecordingHttpClient(204);
        NoticeChannelSender sender = new WebhookNoticeChannelSender(properties, http, new ObjectMapper());
        SysUserService users = mock(SysUserService.class);
        when(users.getById(22L)).thenReturn(user(22L, 8L));

        NoticeChannelResult result = dispatcher(sender, users).dispatch(request(NoticeChannel.WEBHOOK), 22L);

        assertThat(result.delivered()).isFalse();
        assertThat(result.code()).isEqualTo("TENANT_MISMATCH");
        assertThat(http.uri).isNull();
    }

    @Test
    void missingEmailAddressIsReportedWithoutCallingProvider() {
        TenantContextHolder.set(7L);
        NoticeChannelProperties properties = new NoticeChannelProperties();
        properties.getChannels().getEmail().setEnabled(true);
        properties.getChannels().getEmail().setEndpoint("https://mail.example.test/send");
        RecordingHttpClient http = new RecordingHttpClient(202);
        NoticeChannelSender sender = new EmailNoticeChannelSender(properties, http, new ObjectMapper());
        SysUserService users = mock(SysUserService.class);
        when(users.getById(22L)).thenReturn(user(22L, 7L));

        NoticeChannelResult result = dispatcher(sender, users).dispatch(request(NoticeChannel.EMAIL), 22L);

        assertThat(result.delivered()).isFalse();
        assertThat(result.code()).isEqualTo("RECIPIENT_ADDRESS_MISSING");
        assertThat(http.uri).isNull();
    }

    private static NoticeChannelDispatcher dispatcher(NoticeChannelSender sender, SysUserService users) {
        return new NoticeChannelDispatcher(List.of(sender), users);
    }

    private static NoticeChannelRequest request(NoticeChannel channel) {
        return new NoticeChannelRequest(channel, 7L, 99L, "hello", "body");
    }

    private static SysUser user(Long id, Long tenantId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setTenantId(tenantId);
        return user;
    }

    private static final class RecordingHttpClient implements NoticeHttpClient {
        private final int status;
        private URI uri;
        private String body;
        private Map<String, String> headers;

        private RecordingHttpClient(int status) {
            this.status = status;
        }

        @Override
        public NoticeHttpResponse post(URI uri, String body, Map<String, String> headers,
                                       java.time.Duration timeout) {
            this.uri = uri;
            this.body = body;
            this.headers = headers;
            return new NoticeHttpResponse(status, "ok");
        }
    }
}
