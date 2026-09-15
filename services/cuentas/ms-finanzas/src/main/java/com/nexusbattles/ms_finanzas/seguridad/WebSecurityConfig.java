package com.nexusbattles.ms_finanzas.seguridad;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra el {@link SecurityInterceptor} y la política CORS local. Sigue el
 * mismo patrón que {@code WebSecurityConfig} de ms-identidad para no
 * introducir divergencia entre servicios del dominio cuentas.
 *
 * <p>El CORS permisivo aplica solo al conjunto de rutas del servicio; los
 * headers y métodos abiertos aquí son los que el frontend usa desde otro
 * origen durante desarrollo. En producción el mismo Spring Boot sirve el
 * frontend estático de cuentas (via ms-identidad), así que este CORS deja
 * de tener efecto porque las peticiones son mismo-origen.
 */
@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    private final SecurityInterceptor securityInterceptor;

    public WebSecurityConfig(SecurityInterceptor securityInterceptor) {
        this.securityInterceptor = securityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(securityInterceptor);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
