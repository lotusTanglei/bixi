package com.lotus.bixi;

import com.lotus.bixi.common.security.annotation.EnableBixiResourceServer;
import com.lotus.bixi.common.swagger.annotation.EnableBixiDoc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author tanglei
 * @description TODO
 * @date 2024/12/8 22:11
 */
@EnableBixiDoc(value = "admin", isMicro = false)
@EnableBixiResourceServer
@SpringBootApplication
public class BixiSingleApplication {

    public static void main(String[] args) {
        SpringApplication.run(BixiSingleApplication.class, args);

        System.out.println("                               ___-----___\n" +
                "                          _-~~             ~~-_\n" +
                "                      _-~                    /~-_\n" +
                "   /^\\__/^\\          /~  \\                   /    \\\n" +
                "  /|  O|| O|       /     \\_______________/          \\\n" +
                " |  --- --- \\    /      /                    \\        \\\n" +
                " |   (_______) /______/      BIXI-SINGLE       \\_______ \\\n" +
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
