package com.nexusbattles.ms_identidad.rbac.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    private final SecurityInterceptor securityInterceptor;

    /**
     * Origenes que pueden llamar a la API desde un navegador.
     *
     * <p>R9.5 — aqui habia {@code allowedOriginPatterns("*")}. Con comodin,
     * cualquier pagina de cualquier dominio podia lanzar peticiones a esta API
     * desde el navegador de quien la visitara. El Bearer no viaja solo en esas
     * peticiones, asi que no es robo de sesion por si mismo; lo que si daba
     * era superficie gratis, y sobre todo contradecia la regla 10 de
     * plataforma: la configuracion se fija por variable de entorno, no
     * escrita en el codigo.
     *
     * <p>El valor por omision cubre el desarrollo local real —el borde en
     * :8099 y el propio servicio— y deja fuera al resto. En AWS lo fija
     * {@code IDENTIDAD_CORS_ORIGENES} con el origen del borde.
     */
    private final String[] origenesPermitidos;

    /** Desarrollo local real: el borde y el propio servicio. Nada mas. */
    static final String[] ORIGENES_POR_OMISION = {
        "http://localhost:8099", "http://127.0.0.1:8099",
        "http://localhost:8089", "http://127.0.0.1:8089"
    };

    public WebSecurityConfig(
            SecurityInterceptor securityInterceptor,
            @Value("${app.seguridad.cors-origenes:}") String[] origenesConfigurados) {
        this.securityInterceptor = securityInterceptor;
        this.origenesPermitidos = normalizar(origenesConfigurados);
    }

    /**
     * Una lista vacia no puede significar "ninguno": significaria que el
     * despliegue paso la variable sin valor y la API dejaria de responder a
     * toda peticion con origen, sin que nadie entendiera por que. Ante eso se
     * cae a la lista de desarrollo, que es restrictiva pero utilizable.
     */
    private static String[] normalizar(String[] configurados) {
        if (configurados == null) {
            return ORIGENES_POR_OMISION.clone();
        }
        String[] limpios = java.util.Arrays.stream(configurados)
                .filter(o -> o != null && !o.isBlank())
                .map(String::trim)
                .toArray(String[]::new);
        return limpios.length == 0 ? ORIGENES_POR_OMISION.clone() : limpios;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(securityInterceptor);
    }

    /**
     * CORS para la demo HTML/JS servida desde un origen distinto al del
     * servicio. No sustituye same-origin cuando el estatico lo sirva Spring.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOriginPatterns(origenesPermitidos)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
