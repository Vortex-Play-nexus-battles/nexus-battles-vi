package com.nexusbattles.ms_identidad.auth.correo;

import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoAvisoAccesoRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoBienvenidaRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CorreoClient {

    private static final Logger log = LoggerFactory.getLogger(CorreoClient.class);

    private final RestClient restClient;

    @Value("${app.correo.url-bienvenida}")
    private String urlBienvenida;

    @Value("${app.correo.url-aviso-acceso}")
    private String urlAvisoAcceso;

    public CorreoClient(RestClient correoRestClient) {
        this.restClient = correoRestClient;
    }

    // Orden por defecto de Resilience4j: Retry envuelve a CircuitBreaker.
    // El respaldo va en @Retry, no en @CircuitBreaker: así se ejecuta solo
    // cuando ya se agotaron los reintentos (mismo patrón que ListaNegraClient).
    @Retry(name = "correo", fallbackMethod = "enviarBienvenidaConFallback")
    @CircuitBreaker(name = "correo")
    public void enviarBienvenida(CorreoBienvenidaRequest datos) {
        restClient.post()
            .uri(urlBienvenida)
            .body(datos)
            .retrieve()
            .toBodilessEntity();
    }

    private void enviarBienvenidaConFallback(CorreoBienvenidaRequest datos, Throwable ex) {
        log.warn("Servicio de correo no disponible, no se pudo enviar el correo de bienvenida a '{}'. Motivo: {}",
            datos.getEmail(), ex.getMessage());
    }

    @Retry(name = "correo", fallbackMethod = "enviarAvisoAccesoConFallback")
    @CircuitBreaker(name = "correo")
    public void enviarAvisoAcceso(CorreoAvisoAccesoRequest datos) {
        restClient.post()
            .uri(urlAvisoAcceso)
            .body(datos)
            .retrieve()
            .toBodilessEntity();
    }

    private void enviarAvisoAccesoConFallback(CorreoAvisoAccesoRequest datos, Throwable ex) {
        log.warn("Servicio de correo no disponible, no se pudo enviar el aviso de acceso a '{}'. Motivo: {}",
            datos.getEmail(), ex.getMessage());
    }
}
