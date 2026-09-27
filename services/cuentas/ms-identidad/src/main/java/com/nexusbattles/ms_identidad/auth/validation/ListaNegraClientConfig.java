package com.nexusbattles.ms_identidad.auth.validation;

import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente hacia la lista negra de moderacion-sanciones.
 *
 * <p>B2: con la credencial de servicio de ms-identidad (ADR-005) y la traza
 * de la peticion (regla 5). La verificacion es publica, pero la respuesta a
 * un servicio autenticado trae el detalle, y entre servicios se habla con
 * credencial.
 */
@Configuration
public class ListaNegraClientConfig {

    @Bean
    public RestClient listaNegraRestClient(CredencialPropia credencial, InterceptorDeTraza traza) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000); // 2 segundos
        requestFactory.setReadTimeout(2000);    // 2 segundos

        return RestClient.builder()
            .requestFactory(requestFactory)
            .requestInterceptor(credencial)
            .requestInterceptor(traza)
            .build();
    }
}
