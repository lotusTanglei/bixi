package com.lotus.bixi.ai;

import com.lotus.bixi.ai.controller.AiController;
import com.lotus.bixi.ai.controller.AiSessionController;
import com.lotus.bixi.ai.service.ChatService;
import com.lotus.bixi.ai.service.MessageService;
import com.lotus.bixi.ai.service.ModelConfigService;
import com.lotus.bixi.ai.service.SessionService;
import com.lotus.bixi.ai.service.VectorStoreService;
import com.lotus.bixi.common.ai.config.AiAutoConfigurationImportFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.StreamUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiConditionalConfigurationTest {

    @Test
    void disabledAiDoesNotRegisterRoutes() {
        new ApplicationContextRunner()
                .withUserConfiguration(AiRoutes.class)
                .withPropertyValues("ai.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(AiController.class)
                        .doesNotHaveBean(AiSessionController.class));
    }

    @Test
    void enabledAiRegistersBothControllerGroups() {
        new ApplicationContextRunner()
                .withUserConfiguration(AiRoutes.class)
                .withPropertyValues("ai.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(AiController.class)
                        .hasSingleBean(AiSessionController.class));
    }

    @Test
    void disabledAiFiltersDashScopeAutoConfigurationsBeforeProviderCreation() {
        AiAutoConfigurationImportFilter filter = new AiAutoConfigurationImportFilter();
        filter.setEnvironment(new MockEnvironment().withProperty("ai.enabled", "false"));
        boolean[] matches = filter.match(new String[] {
                "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration",
                "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"
        }, (AutoConfigurationMetadata) null);

        assertThat(matches).containsExactly(false, true);
    }

    @Test
    void applicationDefaultsKeepAiOptIn() throws IOException {
        String yaml = StreamUtils.copyToString(
                new ClassPathResource("application.yml").getInputStream(), StandardCharsets.UTF_8);

        assertThat(yaml).contains(
                "ai:\n  enabled: ${AI_ENABLED:false}",
                "enabled: ${AI_CHAT_ENABLED:${AI_ENABLED:false}}",
                "enabled: ${AI_EMBEDDING_ENABLED:${AI_ENABLED:false}}");
    }

    @Configuration(proxyBeanMethods = false)
    @org.springframework.context.annotation.Import({AiController.class, AiSessionController.class})
    static class AiRoutes {

        @Bean
        ChatService chatService() {
            return mock(ChatService.class);
        }

        @Bean
        VectorStoreService vectorStoreService() {
            return mock(VectorStoreService.class);
        }

        @Bean
        SessionService sessionService() {
            return mock(SessionService.class);
        }

        @Bean
        MessageService messageService() {
            return mock(MessageService.class);
        }

        @Bean
        ModelConfigService modelConfigService() {
            return mock(ModelConfigService.class);
        }
    }
}
