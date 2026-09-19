package com.nexusbattles.comun.seguridad.servicio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterceptorDePortadorDeServicio · Authorization: Bearer en cada peticion saliente")
class InterceptorDePortadorDeServicioTest {

    private static MockClientHttpRequest peticion() {
        return new MockClientHttpRequest(HttpMethod.PUT, URI.create("http://inventario/api/v1/x"));
    }

    private static ClientHttpResponse ok() {
        return new MockClientHttpResponse(new byte[0], 200);
    }

    @Test
    @DisplayName("pone la credencial del servicio, y la pide una vez por peticion")
    void poneLaCredencial() throws IOException {
        AtomicInteger pedidas = new AtomicInteger();
        InterceptorDePortadorDeServicio interceptor =
                new InterceptorDePortadorDeServicio(() -> "token-" + pedidas.incrementAndGet());
        MockClientHttpRequest peticion = peticion();

        interceptor.intercept(peticion, new byte[0], (p, c) -> ok());

        assertThat(peticion.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-1");
    }

    @Test
    @DisplayName("si la peticion ya trae Authorization, no la pisa")
    void respetaUnaCredencialPuestaAMano() throws IOException {
        InterceptorDePortadorDeServicio interceptor = new InterceptorDePortadorDeServicio(() -> "servicio");
        MockClientHttpRequest peticion = peticion();
        peticion.getHeaders().setBearerAuth("otra");

        interceptor.intercept(peticion, new byte[0], (p, c) -> ok());

        assertThat(peticion.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer otra");
    }
}
