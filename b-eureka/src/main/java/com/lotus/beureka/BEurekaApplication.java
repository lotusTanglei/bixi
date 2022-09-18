package com.lotus.beureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class BEurekaApplication {

	public static void main(String[] args) {
		SpringApplication.run(BEurekaApplication.class, args);
	}

}
