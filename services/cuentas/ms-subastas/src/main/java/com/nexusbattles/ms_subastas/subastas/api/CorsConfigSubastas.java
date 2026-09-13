package com.nexusbattles.ms_subastas.subastas.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * HU-SUB-011. Habilita CORS para que el frontend, servido hoy por
 * ms-identidad (puerto 8089) via WebConfig.java, pueda consultar este
 * microservicio (puerto 8092) desde el navegador. Sin esto, cualquier
 * fetch desde una pagina HTML de otro origen es bloqueado por el propio
 * navegador antes de que la peticion llegue siquiera al controlador.
 *
 * Acotado a /subastas/** (basta con lo publico de esta HU) y a los
 * origenes de desarrollo local conocidos -- no un wildcard "*" abierto a
 * cualquier origen, ni una configuracion global para todo el servicio.
 *
 * PENDIENTE EQUIPO: esto es una solucion de desarrollo local, igual que el
 * TODO ya existente en WebConfig.java de ms-identidad sobre servir el
 * frontend directamente. Cuando exista el API Gateway que menciona
 * backend-spring.md como "entrada unica", el frontend dejaria de llamar
 * a cada microservicio por su propio puerto, y esta configuracion
 * probablemente se retire o se centralice ahi.
 */
@Configuration
public class CorsConfigSubastas implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/subastas/**")
            .allowedOrigins("http://localhost:8089", "http://localhost:5500")
            .allowedMethods("GET")
            .allowedHeaders("*");
    }
}
