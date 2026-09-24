package com.nexusbattles.ms_ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.nexusbattles.ms_ecommerce.client")
public class MsEcommerceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsEcommerceApplication.class, args);
	}

}
