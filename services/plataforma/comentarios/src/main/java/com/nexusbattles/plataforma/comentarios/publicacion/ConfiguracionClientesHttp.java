package com.nexusbattles.plataforma.comentarios.publicacion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP del servicio de comentarios.
 *
 * <p>El builder lo entrega Spring Boot ya configurado, y tener el bean aqui
 * deja un solo lugar donde anadir despues la propagacion del trace id que
 * exige la regla 5 de plataforma para toda llamada entre servicios.
 */
@Configuration
class ConfiguracionClientesHttp {

    @Bean
    RestClient restClientComentarios(RestClient.Builder builder) {
        return builder.build();
    }
}
