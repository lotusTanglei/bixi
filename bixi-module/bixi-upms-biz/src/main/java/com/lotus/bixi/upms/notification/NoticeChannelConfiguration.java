package com.lotus.bixi.upms.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NoticeChannelProperties.class)
public class NoticeChannelConfiguration {
}
