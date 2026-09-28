package com.nexusbattles.ms_ecommerce.traza;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Pone la traza en cada llamada saliente de la tienda: {@code traceparent}
 * (W3C, regla 5) y {@code X-Trace-Id}, que es la cabecera que declara el
 * contrato de correo para correlacionar sus envios.
 */
public final class InterceptorDeTraza implements ClientHttpRequestInterceptor {

    public static final String CABECERA = "traceparent";
    public static final String CABECERA_DE_CORREO = "X-Trace-Id";

    @Override
    public ClientHttpResponse intercept(HttpRequest peticion, byte[] cuerpo,
                                        ClientHttpRequestExecution ejecucion) throws IOException {
        Traza.traceparentHijo().ifPresent(valor -> peticion.getHeaders().set(CABECERA, valor));
        Traza.actual().ifPresent(traceId -> peticion.getHeaders().set(CABECERA_DE_CORREO, traceId));
        return ejecucion.execute(peticion, cuerpo);
    }
}
