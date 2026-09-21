package com.nexusbattles.plataforma.comentarios.publicacion;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP del servicio de comentarios.
 *
 * <p>El builder lo entrega Spring Boot ya configurado, y tener el bean aqui
 * deja un solo lugar donde anadir despues la propagacion del trace id que
 * exige la regla 5 de plataforma para toda llamada entre servicios.
 *
 * <p>Si el servicio tiene credencial propia configurada
 * ({@code DIRECTORIO_ACTIVO_*}, ADR-001/ADR-005), la lleva en cada llamada a
 * moderacion-sanciones; si no, llama como hasta ahora. Asi la verificacion de
 * lista negra puede cerrarse a tokens de servicio cuando su dueno lo decida,
 * sin tocar este servicio.
 */
@Configuration
class ConfiguracionClientesHttp {

    @Bean
    RestClient restClientComentarios(RestClient.Builder builder,
                                     ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        credencial.ifAvailable(builder::requestInterceptor);
        return builder.build();
    }
}
