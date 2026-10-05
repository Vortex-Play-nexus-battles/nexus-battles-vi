package com.nexusbattles.ms_identidad.auth.correo;

import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia el servicio de correo.
 *
 * <p>Lleva la credencial de servicio de ms-identidad en cada peticion
 * ({@link CredencialPropia}, ADR-005): correo solo atiende a servicios
 * autenticados, porque desde el se pueden enviar codigos de recuperacion y
 * de confirmacion de cuenta a cualquier direccion. Y la traza de la peticion
 * que lo origino (regla 5, {@link InterceptorDeTraza}).
 */
@Configuration
public class CorreoClientConfig {

    @Bean
    public RestClient correoRestClient(CredencialPropia credencial, InterceptorDeTraza traza) {
        // R18: cinco segundos, no dos. Correo entrega en segundo plano y
        // responde en cuanto acepta, asi que dos bastarian; pero dos segundos
        // para una llamada entre servicios no deja margen para un arranque en
        // frio ni para un host cargado, y agotarlos aqui dispara el reintento
        // -- que con la entrega sincrona anterior mandaba el mismo correo dos
        // veces, pagando cuota dos veces y con el jugador recibiendo dos
        // copias del mismo codigo. Desde B1 el reintento lleva Idempotency-Key.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);

        return RestClient.builder()
            .requestFactory(requestFactory)
            .requestInterceptor(credencial)
            .requestInterceptor(traza)
            .build();
    }
}
