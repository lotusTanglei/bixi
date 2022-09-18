package com.lotus.bsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
public class BSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(BSystemApplication.class, args);
	}

}
