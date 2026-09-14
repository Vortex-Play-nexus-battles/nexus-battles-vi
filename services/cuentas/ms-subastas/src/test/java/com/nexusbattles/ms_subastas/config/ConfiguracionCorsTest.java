package com.nexusbattles.ms_subastas.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguracionCorsTest {

    @Test
    void registraMapeosCorsCorrectamenteConOrigenesRequeridos() {
        List<String> origenes = List.of(
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "http://localhost:8089",
                "http://127.0.0.1:8089",
                "http://localhost:5500",
                "http://127.0.0.1:5500"
        );
        ConfiguracionCors config = new ConfiguracionCors(origenes);

        TestCorsRegistry registry = new TestCorsRegistry();
        config.addCorsMappings(registry);

        Map<String, CorsConfiguration> configs = registry.obtenerConfiguraciones();
        assertTrue(configs.containsKey("/**"), "Debe registrar mapeo global /**");

        CorsConfiguration cors = configs.get("/**");
        assertNotNull(cors);
        assertTrue(cors.getAllowedOrigins().contains("http://localhost:8089"), "Debe permitir origen de ms-identidad (8089)");
        assertTrue(cors.getAllowedOrigins().contains("http://localhost:5500"), "Debe permitir Live Server (5500)");
        assertTrue(cors.getAllowedOrigins().contains("http://localhost:8080"), "Debe permitir puerto estándar de frontend (8080)");
        assertTrue(cors.getAllowedHeaders().contains("Idempotency-Key"), "Debe permitir cabecera Idempotency-Key");
        assertTrue(cors.getAllowedHeaders().contains("Authorization"), "Debe permitir cabecera Authorization");
        assertTrue(cors.getAllowedMethods().containsAll(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")));
    }

    private static class TestCorsRegistry extends CorsRegistry {
        public Map<String, CorsConfiguration> obtenerConfiguraciones() {
            return super.getCorsConfigurations();
        }
    }
}
