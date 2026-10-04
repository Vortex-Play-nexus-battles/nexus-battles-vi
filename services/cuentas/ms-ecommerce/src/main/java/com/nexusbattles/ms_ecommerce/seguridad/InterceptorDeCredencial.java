package com.nexusbattles.ms_ecommerce.seguridad;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Objects;

/**
 * Pone la credencial de la tienda en cada peticion saliente de un
 * {@code RestClient} — el {@code InterceptorDePortadorDeServicio} de
 * plataforma-seguridad, reproducido para este modulo Maven.
 *
 * <p>Si la peticion ya trae {@code Authorization}, se respeta.
 */
public final class InterceptorDeCredencial implements ClientHttpRequestInterceptor {

    private final CredencialDeServicio credencial;

    public InterceptorDeCredencial(CredencialDeServicio credencial) {
        this.credencial = Objects.requireNonNull(credencial, "credencial");
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest peticion, byte[] cuerpo,
                                        ClientHttpRequestExecution ejecucion) throws IOException {
        if (!peticion.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            peticion.getHeaders().setBearerAuth(credencial.portador());
        }
        return ejecucion.execute(peticion, cuerpo);
    }
}
