package com.lotus.bixi.ai;

import com.lotus.bixi.common.datasource.annotation.EnableDynamicDataSource;
import com.lotus.bixi.common.feign.annotation.EnableBixiFeignClients;
import com.lotus.bixi.common.security.annotation.EnableBixiResourceServer;
import com.lotus.bixi.common.swagger.annotation.EnableBixiDoc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Bixi AI 服务启动类
 *
 * @author bixi
 * @date 2025-01-01
 */
@EnableDynamicDataSource
@EnableBixiDoc(value = "ai")
@EnableBixiFeignClients
@EnableBixiResourceServer
@EnableDiscoveryClient
@SpringBootApplication
public class BixiAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BixiAiApplication.class, args);
        System.out.println("    ____  __  ______  ___________   ____  ____  _____\n" +
                "   / __ )/ / / / __ \\/ ____/  _/ | / / / / / / / /\n" +
                "  / __  / /_/ / / / / / __ / //  |/ / / / / / / / \n" +
                " / /_/ / __  / /_/ / /_/ // // /|  / /_/ / /_/ /  \n" +
                "/_____/_/ /_/\\____/\\____/___/_/ |_/\\____/\\____/   \n" +
                "                                                  \n" +
                "  Bixi AI Service Started Successfully!");
    }
}
