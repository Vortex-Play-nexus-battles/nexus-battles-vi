package com.nexusbattles.ms_ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * La tienda (ms-ecommerce).
 *
 * <p>{@code @EnableFeignClients}: el cliente de ms-finanzas usado por el pago.
 * <p>{@code @EnableScheduling}: la tarea que termina las compras a medias
 * ({@code ReanudadorDeOrdenes}, B5).
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.nexusbattles.ms_ecommerce.client")
@EnableScheduling
public class MsEcommerceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsEcommerceApplication.class, args);
	}

}
