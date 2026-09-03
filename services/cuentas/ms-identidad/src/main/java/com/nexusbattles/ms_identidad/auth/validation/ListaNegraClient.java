package com.nexusbattles.ms_identidad.auth.validation;

import com.nexusbattles.ms_identidad.auth.validation.dto.ListaNegraRequest;
import com.nexusbattles.ms_identidad.auth.validation.dto.ListaNegraResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ListaNegraClient {

    private static final Logger log = LoggerFactory.getLogger(ListaNegraClient.class);

    private final RestClient restClient;

    @Value("${app.lista-negra.url}")
    private String urlListaNegra;

    public ListaNegraClient(RestClient listaNegraRestClient) {
        this.restClient = listaNegraRestClient;
    }

    // Orden por defecto de Resilience4j: Retry envuelve a CircuitBreaker.
    // Por eso el respaldo va en @Retry, no en @CircuitBreaker: así se
    // ejecuta solo cuando YA se agotaron los reintentos.
    @Retry(name = "listaNegra", fallbackMethod = "verificarConFallback")
    @CircuitBreaker(name = "listaNegra")
    public ListaNegraResponse verificar(String texto) {
        return restClient.post()
            .uri(urlListaNegra)
            .body(new ListaNegraRequest(texto))
            .retrieve()
            .body(ListaNegraResponse.class);
    }

    // Se ejecuta cuando el servicio de Felipe no responde (caído, timeout,
    // circuito abierto), después de agotar los reintentos. Deja pasar el
    // apodo (fail-open, como se decidió), pero SIEMPRE deja constancia en
    // el log — nunca en silencio.
    private ListaNegraResponse verificarConFallback(String texto, Throwable ex) {
        log.warn("Servicio de lista negra no disponible, se permite el apodo '{}' sin verificar externamente. Motivo: {}",
            texto, ex.getMessage());

        ListaNegraResponse respuestaFallback = new ListaNegraResponse();
        respuestaFallback.setAprobado(true);
        respuestaFallback.setMotivo(null);
        return respuestaFallback;
    }
}
