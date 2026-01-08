package com.lotus.bixi.monitor;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@EnableAdminServer
@EnableDiscoveryClient
@SpringBootApplication
public class BixiMonitorApplication {

	public static void main(String[] args) {
		SpringApplication.run(BixiMonitorApplication.class, args);

		System.out.println("                               ___-----___\n" +
				"                          _-~~             ~~-_\n" +
				"                      _-~                    /~-_\n" +
				"   /^\\__/^\\          /~  \\                   /    \\\n" +
				"  /|  O|| O|       /     \\_______________/          \\\n" +
				" |  --- --- \\    /      /                    \\        \\\n" +
				" |   (_______) /______/      BIXI-MONITOR      \\_______ \\\n" +
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
