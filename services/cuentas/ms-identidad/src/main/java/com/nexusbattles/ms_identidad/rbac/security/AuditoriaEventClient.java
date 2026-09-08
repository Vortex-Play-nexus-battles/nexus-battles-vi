package com.nexusbattles.ms_identidad.rbac.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Cliente REST asíncrono y resiliente para registrar eventos de auditoría
 * de seguridad en ms-cumplimiento (POST /api/v1/admin/auditoria/eventos).
 */
@Component
public class AuditoriaEventClient {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaEventClient.class);
    private final RestClient restClient;
    private final String urlAuditoria;

    public AuditoriaEventClient(
            @Value("${app.auditoria.url:http://localhost:8083/api/v1/admin/auditoria/eventos}") String urlAuditoria) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000); // 1 segundo
        requestFactory.setReadTimeout(1000);    // 1 segundo

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.urlAuditoria = urlAuditoria;
    }

    public void registrarBypassAsync(String username, String role, String action, String reason, String ipOrigen) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, String> solicitud = Map.of(
                        "tipoAccion", "SECURITY_BYPASS_ATTEMPT",
                        "administradorId", username != null ? username : "ANONYMOUS",
                        "afectado", action != null ? action : "UNKNOWN_ACTION",
                        "valorAnterior", role != null ? role : "NONE",
                        "valorNuevo", "403_FORBIDDEN",
                        "motivo", reason != null ? reason : "INTENTO_NO_AUTORIZADO",
                        "ipOrigen", ipOrigen != null ? ipOrigen : "127.0.0.1"
                );

                restClient.post()
                        .uri(urlAuditoria)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(solicitud)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception ex) {
                // Fail-safe: si ms-cumplimiento no está disponible, no bloquea ni afecta la respuesta 403
                log.debug("No se pudo notificar evento a ms-cumplimiento: {}", ex.getMessage());
            }
        });
    }
}
