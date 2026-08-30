package com.nexusbattles.ms_identidad.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// TODO EQUIPO: esta configuracion es una solucion de desarrollo LOCAL para
// poder probar frontend/app-web/src/cuentas/ y frontend/app-web/src/comun/
// como recursos estaticos servidos por ms-identidad, mientras no exista un
// Spring Cloud Gateway (mencionado en backend-spring.md como "entrada
// unica") u otro mecanismo oficial de servir el frontend completo.
// Confirmar con el equipo si este es el patron definitivo, o si mas
// adelante el frontend se sirve desde otro lugar (Gateway, servicio
// dedicado, etc.) y esto se retira.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.frontend.ruta:file:../../../frontend/app-web/src/}")
    private String rutaFrontend;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/cuentas/**")
            .addResourceLocations(rutaFrontend + "cuentas/");

        registry.addResourceHandler("/comun/**")
            .addResourceLocations(rutaFrontend + "comun/");
    }
}
