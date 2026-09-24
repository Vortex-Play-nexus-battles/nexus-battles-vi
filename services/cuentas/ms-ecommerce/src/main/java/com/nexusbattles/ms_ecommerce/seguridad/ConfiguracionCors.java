package com.nexusbattles.ms_ecommerce.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Origenes desde los que el navegador puede llamar a la tienda.
 *
 * <p><b>Por que existe (R16).</b> Los controladores llevaban
 * {@code @CrossOrigin} con {@code localhost:8080} escrito a mano. Cuando la
 * vista se sirve desde el borde, el navegador manda {@code Origin} en cada
 * {@code POST}. Ese origen no estaba en la lista, asi que la peticion se
 * trataba como CORS de un origen ajeno y Spring la rechazaba con 403, aunque el
 * token fuera valido. Los {@code GET} no mandan {@code Origin} y por eso la
 * vitrina y el carrito si cargaban. El que fallaba era «Añadir», justo el que
 * nadie habia podido pulsar mientras la vitrina salia vacia. Lo destapo la
 * prueba E2E de la tienda: la API respondia 200 y la pantalla recibia 403.
 *
 * <p><b>Lista explicita por variable de entorno, no un comodin.</b> Es el mismo
 * patron que {@code ConfiguracionCors} de ms-subastas (regla 10 de
 * plataforma). El carrito es de quien trae el token y el token viaja en una
 * cabecera, no en una cookie, asi que un comodin hoy no abriria nada. Una lista
 * explicita no cuesta nada y no depende de que eso siga asi. En dev,
 * {@code CORS_ORIGENES} es el origen del borde.
 */
@Configuration
public class ConfiguracionCors implements WebMvcConfigurer {

    private final List<String> origenesPermitidos;

    public ConfiguracionCors(
            @Value("${app.cors.origenes-permitidos:http://localhost:8080,http://127.0.0.1:8080}")
            List<String> origenesPermitidos) {
        this.origenesPermitidos = origenesPermitidos;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Las rutas van sin /ecommerce: el context-path ya lo antepone.
        registry.addMapping("/api/**")
                .allowedOrigins(origenesPermitidos.toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .maxAge(3600);
    }
}
