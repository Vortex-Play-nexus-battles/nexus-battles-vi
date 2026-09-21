package com.nexusbattles.ms_finanzas;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MsFinanzasApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsFinanzasApplication.class, args);
    }

    // Se inyecta un Clock (mismo patrón que ms-subastas) para que los servicios
    // de dominio no dependan de Instant.now() estático y puedan congelar el
    // tiempo en pruebas. Cambiar el bean por Clock.fixed(...) en un test basta
    // para verificar reglas basadas en fecha (idempotencia, retención, etc.).
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
