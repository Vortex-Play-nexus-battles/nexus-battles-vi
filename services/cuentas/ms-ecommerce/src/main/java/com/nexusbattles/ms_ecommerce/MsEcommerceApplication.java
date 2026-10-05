package com.nexusbattles.ms_ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * La tienda (ms-ecommerce).
 *
 * <p>{@code @EnableScheduling}: la tarea que termina las compras a medias
 * ({@code ReanudadorDeOrdenes}, B5).
 */
@SpringBootApplication
@EnableScheduling
public class MsEcommerceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsEcommerceApplication.class, args);
	}

}
