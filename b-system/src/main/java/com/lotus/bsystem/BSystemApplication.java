package com.lotus.bsystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
@MapperScan("com.loyus.bsystem.mapper")
public class BSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(BSystemApplication.class, args);
	}

}
