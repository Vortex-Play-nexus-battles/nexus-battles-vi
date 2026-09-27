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

/**
 * Lista negra de terminos prohibidos (HU-ADM-002) en moderacion-sanciones.
 *
 * <p><b>B2: fail-closed.</b> Hasta aqui, si moderacion no respondia, el
 * respaldo aprobaba el apodo «sin verificar externamente»: bastaba con que el
 * servicio estuviera caido, o con que su cache fallara, para que cualquier
 * apodo entrara. Ahora el respaldo LANZA {@link ModeracionNoDisponibleException}
 * y quien registra, crea o cambia un apodo responde 503: un apodo que nadie
 * comprobo no se da por bueno. La persona reintenta en unos segundos.
 */
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
    public ListaNegraResponse verificar(String texto, String contexto) {
        return restClient.post()
            .uri(urlListaNegra)
            .body(new ListaNegraRequest(texto, contexto))
            .retrieve()
            .body(ListaNegraResponse.class);
    }

    // Se ejecuta cuando moderacion-sanciones no responde (caido, timeout,
    // circuito abierto, error), despues de agotar los reintentos. Nunca
    // aprueba: el texto sin comprobar no se imprime (puede ser ofensivo, y es
    // un dato de la persona) y la decision sube como 503.
    private ListaNegraResponse verificarConFallback(String texto, String contexto, Throwable ex) {
        log.warn("Lista negra no disponible: el {} no se pudo verificar y se rechaza la operacion. Motivo: {}",
            contexto, ex.getMessage());
        throw new ModeracionNoDisponibleException(
            "No pudimos comprobar el apodo en este momento. Inténtalo de nuevo en unos segundos.", ex);
    }
}
