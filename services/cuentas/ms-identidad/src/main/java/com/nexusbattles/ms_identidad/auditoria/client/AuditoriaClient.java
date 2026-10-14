package com.nexusbattles.ms_identidad.auditoria.client;

import com.nexusbattles.ms_identidad.auditoria.client.dto.RegistrarAuditoriaRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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
                          String valorAnterior, String valorNuevo, String motivo,
                          String ipOrigen) {
        RegistrarAuditoriaRequest solicitud = new RegistrarAuditoriaRequest(
            tipoAccion, administradorId, afectado, valorAnterior, valorNuevo, motivo, ipOrigen
        );
        restClient.post()
            .uri(urlAuditoria)
            .body(solicitud)
            .retrieve()
            .toBodilessEntity();
    }

    /**
     * Como {@link #registrar(String, String, String, String, String, String, String)} con la
     * {@code referencia} del evento (ms-cumplimiento-auditoria 1.3.0): repetir el
     * mismo hecho con la misma referencia no duplica la entrada. La usa la entrega
     * administrativa al inventario (SA-10), que se repite con la misma clave
     * cuando la bitacora no respondio la primera vez.
     */
    @Retry(name = "auditoria", fallbackMethod = "registrarConReferenciaYFallback")
    @CircuitBreaker(name = "auditoria")
    public void registrar(String tipoAccion, String administradorId, String afectado,
                          String valorAnterior, String valorNuevo, String motivo,
                          String ipOrigen, String referencia) {
        RegistrarAuditoriaRequest solicitud = new RegistrarAuditoriaRequest(
            tipoAccion, administradorId, afectado, valorAnterior, valorNuevo, motivo, ipOrigen, referencia
        );
        restClient.post()
            .uri(urlAuditoria)
            .body(solicitud)
            .retrieve()
            .toBodilessEntity();
    }

    private void registrarConReferenciaYFallback(String tipoAccion, String administradorId, String afectado,
                                                 String valorAnterior, String valorNuevo, String motivo,
                                                 String ipOrigen, String referencia, Throwable ex) {
        registrarConFallback(tipoAccion, administradorId, afectado, valorAnterior, valorNuevo, motivo, ipOrigen, ex);
    }

    private void registrarConFallback(String tipoAccion, String administradorId, String afectado,
                                      String valorAnterior, String valorNuevo, String motivo,
                                      String ipOrigen, Throwable ex) {
        log.error("Fallo registrando auditoría ({}, usuario={}): {}. Se cancela la operación.",
            tipoAccion, afectado, ex.getMessage());
        throw new AuditoriaNoDisponibleException(
            "No se pudo completar la operación: el registro de auditoría no respondió. Inténtalo de nuevo en unos segundos.");
    }
}
