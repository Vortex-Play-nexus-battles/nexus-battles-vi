package com.nexusbattles.ms_identidad.notificaciones.client;

import com.nexusbattles.ms_identidad.notificaciones.client.dto.EmitirNotificacionRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

/**
 * Cliente hacia el microservicio de notificaciones (HU-NOT-006), para avisar
 * al usuario afectado cuando un administrador actúa sobre su cuenta
 * (HU-USR-003: "notifica al usuario afectado").
 *
 * FAIL-OPEN a propósito: si el servicio de notificaciones no responde, la
 * operación administrativa (suspender, banear, etc.) NO debe revertirse — el
 * aviso es secundario frente a la acción. Por eso el fallback solo registra un
 * warning y no relanza (mismo criterio que CorreoClient). Esto es lo OPUESTO a
 * AuditoriaClient, que sí es fail-closed porque la auditoría es obligatoria.
 */
@Component
public class NotificacionClient {

    private static final Logger log = LoggerFactory.getLogger(NotificacionClient.class);

    private final RestClient restClient;

    @Value("${app.notificaciones.url}")
    private String urlNotificaciones;

    public NotificacionClient(RestClient notificacionRestClient) {
        this.restClient = notificacionRestClient;
    }

    @Retry(name = "notificaciones", fallbackMethod = "emitirConFallback")
    @CircuitBreaker(name = "notificaciones")
    public void emitir(String usuarioId, String tipo, String titulo, String cuerpo) {
        EmitirNotificacionRequest solicitud = new EmitirNotificacionRequest(
            usuarioId,
            UUID.randomUUID().toString(),
            tipo,
            titulo,
            cuerpo,
            Instant.now()
        );

        restClient.post()
            .uri(urlNotificaciones)
            .body(solicitud)
            .retrieve()
            .toBodilessEntity();
    }

    private void emitirConFallback(String usuarioId, String tipo, String titulo,
                                   String cuerpo, Throwable ex) {
        log.warn("Servicio de notificaciones no disponible, no se pudo avisar al usuario '{}' ({}). Motivo: {}",
            usuarioId, tipo, ex.getMessage());
    }
}
