package com.nexusbattles.ms_finanzas.pagos.correo;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class ConfiguracionClientesHttpPagos {

    @Bean
    RestClient restClientCorreo(RestClient.Builder builder,
                                 ObjectProvider<InterceptorDePortadorDeServicio> credencial) {
        credencial.ifAvailable(builder::requestInterceptor);
        return builder.build();
    }
}
