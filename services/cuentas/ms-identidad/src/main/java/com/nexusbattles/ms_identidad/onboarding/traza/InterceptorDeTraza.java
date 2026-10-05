package com.nexusbattles.ms_identidad.onboarding.traza;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Pone {@code traceparent} en las llamadas salientes del alta (ver {@link Traza}). */
@Component
public class InterceptorDeTraza implements ClientHttpRequestInterceptor {

    public static final String CABECERA = "traceparent";

    @Override
    public ClientHttpResponse intercept(HttpRequest peticion, byte[] cuerpo,
                                        ClientHttpRequestExecution ejecucion) throws IOException {
        Traza.traceparentHijo().ifPresent(valor -> peticion.getHeaders().set(CABECERA, valor));
        return ejecucion.execute(peticion, cuerpo);
    }
}
