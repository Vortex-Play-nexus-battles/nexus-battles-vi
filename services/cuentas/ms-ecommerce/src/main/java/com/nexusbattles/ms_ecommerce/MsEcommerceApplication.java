package com.nexusbattles.ms_ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableFeignClients
public class MsEcommerceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsEcommerceApplication.class, args);
	}

}
