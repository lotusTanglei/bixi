package com.lotus.bsystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableEurekaClient
@MapperScan("com.lotus.bserver.mapper")
@ComponentScan("com.lotus")
public class BSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(BSystemApplication.class, args);
	}

}
