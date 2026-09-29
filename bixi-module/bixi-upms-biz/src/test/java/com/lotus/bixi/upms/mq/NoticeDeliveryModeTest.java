package com.lotus.bixi.upms.mq;

import com.lotus.bixi.upms.mapper.SysNoticeMapper;
import com.lotus.bixi.upms.mapper.SysUserNoticeMapper;
import com.lotus.bixi.upms.service.SysNoticeService;
import com.lotus.bixi.upms.service.SysUserNoticeSseService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NoticeDeliveryModeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DeliveryConfiguration.class);

    @Test
    void singleSelectsOnlyLocalDelivery() {
        runner.withPropertyValues("bixi.deployment.mode=single").run(context -> assertThat(context)
                .hasSingleBean(NoticeDelivery.class)
                .hasSingleBean(LocalNoticeDelivery.class)
                .doesNotHaveBean(RabbitNoticeDelivery.class)
                .doesNotHaveBean(NoticeConsumer.class));
    }

    @Test
    void cloudSelectsOnlyRabbitDeliveryAndConsumer() {
        runner.withPropertyValues("bixi.deployment.mode=cloud").run(context -> assertThat(context)
                .hasSingleBean(NoticeDelivery.class)
                .hasSingleBean(RabbitNoticeDelivery.class)
                .hasSingleBean(NoticeConsumer.class)
                .doesNotHaveBean(LocalNoticeDelivery.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({PublishedNoticeNotifier.class, LocalNoticeDelivery.class,
            RabbitNoticeDelivery.class, NoticeConsumer.class})
    static class DeliveryConfiguration {

        @Bean
        SysNoticeMapper noticeMapper() {
            return mock(SysNoticeMapper.class);
        }

        @Bean
        SysUserNoticeMapper userNoticeMapper() {
            return mock(SysUserNoticeMapper.class);
        }

        @Bean
        SysUserNoticeSseService sseService() {
            return mock(SysUserNoticeSseService.class);
        }

        @Bean
        SysNoticeService noticeService() {
            return mock(SysNoticeService.class);
        }

        @Bean
        RabbitTemplate rabbitTemplate() {
            return mock(RabbitTemplate.class);
        }
    }
}
