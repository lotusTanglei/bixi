/*
 *
 *      Copyright (c) 2018-2025, lengleng All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in the
 *  documentation and/or other materials provided with the distribution.
 *  Neither the name of the pig4cloud.com developer nor the names of its
 *  contributors may be used to endorse or promote products derived from
 *  this software without specific prior written permission.
 *  Author: lengleng (wangiegie@gmail.com)
 *
 */

package com.lotus.bixi.upms;

import com.lotus.bixi.common.feign.annotation.EnableBixiFeignClients;
import com.lotus.bixi.common.security.annotation.EnableBixiResourceServer;
import com.lotus.bixi.common.swagger.annotation.EnableBixiDoc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author 唐磊
 * @date 2018年06月21日
 * <p>
 * 用户统一管理系统
 */
@EnableBixiDoc(value = "admin")
@EnableBixiFeignClients
@EnableBixiResourceServer
@EnableDiscoveryClient
@SpringBootApplication
public class BixiUPMSApplication {

    public static void main(String[] args) {
        SpringApplication.run(BixiUPMSApplication.class, args);

        System.out.println("                               ___-----___\n" +
                "                          _-~~             ~~-_\n" +
                "                      _-~                    /~-_\n" +
                "   /^\\__/^\\          /~  \\                   /    \\\n" +
                "  /|  O|| O|       /     \\_______________/          \\\n" +
                " |  --- --- \\    /      /                    \\        \\\n" +
                " |   (_______) /______/      BIXI-UPMS         \\_______ \\\n" +
                " |         / /         \\       启动成功         /           \\\n" +
                "  \\         \\^\\\\         \\                  /               \\     /\n" +
                "   \\         ||           \\______________/      _-_        //\\__//\n" +
                "    \\       ||------_-~~-_ ------------- \\ --/~   ~\\      || __/)\n" +
                "     ~-----||====/~      |==================|       |/~~~\n" +
                "      (_(__/  ./       /                   \\_\\      \\.\n" +
                "                (_(___/                       \\_____)_)\n"
        );

    }

}
