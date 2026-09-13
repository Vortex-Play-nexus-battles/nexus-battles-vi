package com.nexusbattles.ms_subastas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Permite que la pantalla de subastas, servida desde otro puerto en desarrollo,
 * pueda llamar a esta API. Sin esto el navegador bloquea hasta el listado
 * publico y la vista no carga nada: no es un detalle de configuracion, es la
 * diferencia entre que la historia se pueda usar o no.
 *
 * <p><b>Lista explicita de origenes, no un comodin.</b> ms-identidad usa
 * {@code allowedOriginPatterns("*")}, pero por aqui pasan pujas y compras que
 * mueven creditos: con un comodin, cualquier pagina que el jugador tuviera
 * abierta podria lanzarle peticiones autenticadas si el token viajara en una
 * cookie. Hoy va en una cabecera y el riesgo es menor, pero una lista explicita
 * no cuesta nada y no depende de que eso siga siendo asi.
 *
 * <p>Los origenes se configuran por variable de entorno (regla 10 de
 * plataforma). El valor por defecto es el servidor de desarrollo del frontend;
 * en cualquier despliegue real hay que pasar el dominio de verdad.
 */
@Configuration
public class ConfiguracionCors implements WebMvcConfigurer {

    private static final String CABECERA_IDEMPOTENCIA = "Idempotency-Key";

    private final List<String> origenesPermitidos;

    public ConfiguracionCors(
            @Value("${app.cors.origenes-permitidos:http://localhost:8080,http://127.0.0.1:8080}")
            List<String> origenesPermitidos) {
        this.origenesPermitidos = origenesPermitidos;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Las rutas van sin /api/v1: el context-path ya lo antepone.
        registry.addMapping("/**")
                .allowedOrigins(origenesPermitidos.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // Idempotency-Key hay que declararla: no esta entre las
                // cabeceras que CORS permite por defecto, asi que sin esto el
                // preflight de pujar y comprar falla aunque el resto este bien.
                .allowedHeaders("Authorization", "Content-Type", "Accept", CABECERA_IDEMPOTENCIA)
                .maxAge(3600);
    }
}
