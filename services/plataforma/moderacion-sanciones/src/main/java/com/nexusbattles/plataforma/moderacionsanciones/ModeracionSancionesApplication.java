package com.nexusbattles.plataforma.moderacionsanciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ModeracionSancionesApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModeracionSancionesApplication.class, args);
    }
}