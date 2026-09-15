package com.nexusbattles.comun.seguridad.servicio;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Objects;

/**
 * Pone la credencial del servicio en cada peticion saliente de un
 * {@code RestClient} — ADR-001.
 *
 * <pre>{@code
 * RestClient inventario = RestClient.builder()
 *         .baseUrl(urlDeInventario)
 *         .requestInterceptor(interceptorDePortadorDeServicio)
 *         .build();
 * }</pre>
 *
 * <p>Quien no usa {@code RestClient} (por ejemplo {@code java.net.http.HttpClient})
 * hace lo mismo a mano: {@code .header("Authorization", "Bearer " + token.portador())}.
 *
 * <p>Si la peticion ya trae {@code Authorization}, se respeta: hay llamadas que
 * propagan deliberadamente otra credencial y este interceptor no debe pisarla.
 */
public final class InterceptorDePortadorDeServicio implements ClientHttpRequestInterceptor {

    private final TokenDeServicio token;

    public InterceptorDePortadorDeServicio(TokenDeServicio token) {
        this.token = Objects.requireNonNull(token, "token");
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest peticion, byte[] cuerpo,
                                        ClientHttpRequestExecution ejecucion) throws IOException {
        if (!peticion.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            peticion.getHeaders().setBearerAuth(token.portador());
        }
        return ejecucion.execute(peticion, cuerpo);
    }
}
