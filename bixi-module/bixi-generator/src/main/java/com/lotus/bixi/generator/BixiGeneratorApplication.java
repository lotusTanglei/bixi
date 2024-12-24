

package com.lotus.bixi.generator;

import com.lotus.bixi.common.datasource.annotation.EnableDynamicDataSource;
import com.lotus.bixi.common.feign.annotation.EnableBixiFeignClients;
import com.lotus.bixi.common.security.annotation.EnableBixiResourceServer;
import com.lotus.bixi.common.swagger.annotation.EnableBixiDoc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author 唐磊
 * @date 2018/07/29 代码生成模块
 */
@EnableDynamicDataSource
@EnableBixiFeignClients
@EnableBixiDoc("gen")
@EnableDiscoveryClient
@EnableBixiResourceServer
@SpringBootApplication
public class BixiGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(BixiGeneratorApplication.class, args);

		System.out.println("                               ___-----___\n" +
				"                          _-~~             ~~-_\n" +
				"                      _-~                    /~-_\n" +
				"   /^\\__/^\\          /~  \\                   /    \\\n" +
				"  /|  O|| O|       /     \\_______________/          \\\n" +
				" |  --- --- \\    /      /                    \\        \\\n" +
				" |   (_______) /______/      BIXI-GENERATOR    \\_______ \\\n" +
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
