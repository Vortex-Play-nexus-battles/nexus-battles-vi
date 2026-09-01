package com.nexusbattles.ms_identidad.auditoria.client;

import com.nexusbattles.ms_identidad.auditoria.client.dto.RegistrarAuditoriaRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class AuditoriaClient {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaClient.class);

    private final RestClient restClient;

    @Value("${app.auditoria.url}")
    private String urlAuditoria;

    public AuditoriaClient(RestClient auditoriaRestClient) {
        this.restClient = auditoriaRestClient;
    }

    @Retry(name = "auditoria", fallbackMethod = "registrarConFallback")
    @CircuitBreaker(name = "auditoria")
    public void registrar(String tipoAccion, String administradorId, String afectado,
                          String valorAnterior, String valorNuevo, String motivo) {
        RegistrarAuditoriaRequest solicitud = new RegistrarAuditoriaRequest(
            tipoAccion, administradorId, afectado, valorAnterior, valorNuevo, motivo, null
        );
        restClient.post()
            .uri(urlAuditoria)
            .body(solicitud)
            .retrieve()
            .toBodilessEntity();
    }

    private void registrarConFallback(String tipoAccion, String administradorId, String afectado,
                                      String valorAnterior, String valorNuevo, String motivo,
                                      Throwable ex) {
        if (ex instanceof HttpClientErrorException) {
            log.error("Petición de auditoría inválida ({}, usuario={}): {}",
                tipoAccion, afectado, ex.getMessage());
            throw new IllegalStateException(
                "Error interno registrando auditoría (petición inválida): " + ex.getMessage());
        }
        log.warn("ms-cumplimiento no disponible, auditoría no registrada ({}, usuario={}): {}",
            tipoAccion, afectado, ex.getMessage());
    }
}
