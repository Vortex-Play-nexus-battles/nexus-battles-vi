package com.nexusbattles.ms_identidad.auditoria.client;

import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia el servicio de cumplimiento (auditoria).
 *
 * <p>Lleva la credencial de servicio de ms-identidad ({@link CredencialPropia},
 * ADR-005): la bitacora de auditoria es inmutable y solo debe escribirla un
 * servicio autenticado, no cualquiera que alcance el puerto.
 */
@Configuration
public class AuditoriaClientConfig {

    @Bean
    public RestClient auditoriaRestClient(CredencialPropia credencial) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(2000);

        return RestClient.builder()
            .requestFactory(requestFactory)
            .requestInterceptor(credencial)
            .build();
    }
}
