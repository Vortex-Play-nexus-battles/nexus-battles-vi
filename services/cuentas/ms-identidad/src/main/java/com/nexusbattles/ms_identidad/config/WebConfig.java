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

    // Agregado para HU-SUB-011: subastas.html depende de
    // shared/ui-kit/css/tokens.css (y base.css), que hasta ahora no tenia
    // ningun mapeo -- ni /cuentas/** ni /comun/** lo cubren, porque
    // shared/ vive fuera de frontend/app-web/src/. Se resuelve la ruta
    // desde rutaFrontend + "../../../shared/ui-kit/" porque
    // frontend/app-web/src/ y shared/ui-kit/ son ambos hijos directos de
    // la raiz del monorepo -- misma logica relativa que ya usa
    // rutaFrontend consigo mismo, no un valor inventado aparte.
    @Value("${app.shared-ui-kit.ruta:file:../../../shared/ui-kit/}")
    private String rutaSharedUiKit;

    // TODO EQUIPO: los avatares subidos por usuarios se guardan en disco
    // local del servidor (ver AvatarStorageService). Igual que el frontend
    // de arriba, es una solucion de desarrollo LOCAL — si ms-identidad se
    // despliega con multiples instancias, cada una tendria su propia
    // carpeta y las fotos no serian visibles desde todas. Confirmar con
    // el equipo si mas adelante esto migra a un almacenamiento compartido
    // (ej. AWS S3).
    @Value("${app.avatares.ruta-almacenamiento:./avatares-subidos}")
    private String rutaAvatares;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/cuentas/**")
            .addResourceLocations(rutaFrontend + "cuentas/");

        registry.addResourceHandler("/comun/**")
            .addResourceLocations(rutaFrontend + "comun/");

        // HU-SUB-011: expone shared/ui-kit/ bajo /shared/ui-kit/**, para
        // que cualquier pagina en /cuentas/** o /comun/** pueda referenciar
        // sus hojas de estilo con una ruta absoluta simple.
        registry.addResourceHandler("/shared/ui-kit/**")
            .addResourceLocations(rutaSharedUiKit);

        registry.addResourceHandler("/avatares-subidos/**")
            .addResourceLocations("file:" + rutaAvatares + "/");
    }
}
