package com.nexusbattles.plataforma.correo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CorreoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CorreoApplication.class, args);
    }

    /**
     * El reloj se inyecta para que las pruebas puedan fijar el instante de un
     * envio. En produccion es el del sistema.
     */
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}