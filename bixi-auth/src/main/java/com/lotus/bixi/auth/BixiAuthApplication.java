package com.lotus.bixi.auth;

import com.lotus.bixi.common.feign.annotation.EnableBixiFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author 唐磊
 * @date 2018年06月21日 认证授权中心
 */
@EnableBixiFeignClients
@EnableDiscoveryClient
@SpringBootApplication
public class BixiAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(BixiAuthApplication.class, args);

        System.out.println("                               ___-----___\n" +
                "                          _-~~             ~~-_\n" +
                "                      _-~                    /~-_\n" +
                "   /^\\__/^\\          /~  \\                   /    \\\n" +
                "  /|  O|| O|       /     \\_______________/          \\\n" +
                " |  --- --- \\    /      /                    \\        \\\n" +
                " |   (_______) /______/      BIXI-AUTH         \\_______ \\\n" +
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
