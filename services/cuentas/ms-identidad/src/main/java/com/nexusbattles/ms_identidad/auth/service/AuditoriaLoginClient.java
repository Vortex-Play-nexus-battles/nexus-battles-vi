package com.nexusbattles.ms_identidad.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AuditoriaLoginClient {

    private static final Logger log =
        LoggerFactory.getLogger(AuditoriaLoginClient.class);

    private final RestClient restClient;
    private final String urlAuditoria;

    public AuditoriaLoginClient(
        @Value("${app.auditoria.url:http://localhost:8091/api/v1/admin/auditoria/eventos}")
        String urlAuditoria) {

        SimpleClientHttpRequestFactory factory =
            new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(1000);
        factory.setReadTimeout(1000);

        this.restClient = RestClient.builder()
            .requestFactory(factory)
            .build();

        this.urlAuditoria = urlAuditoria;
    }

    public void registrarLoginFallido(String email, String ipOrigen) {

        String afectado =
            email == null || email.isBlank()
                ? "DESCONOCIDO"
                : email;

        String ip =
            ipOrigen == null || ipOrigen.isBlank()
                ? "DESCONOCIDA"
                : ipOrigen;

        AuditoriaLoginRequest solicitud =
            new AuditoriaLoginRequest(
                "OTRO",
                "ANONYMOUS",
                afectado,
                null,
                null,
                "CREDENCIALES_INVALIDAS_ENTORNO",
                ip
            );

        try {
            ResponseEntity<Void> respuesta = restClient.post()
                .uri(urlAuditoria)
                .body(solicitud)
                .retrieve()
                .toBodilessEntity();

            if (respuesta.getStatusCode() != HttpStatus.CREATED) {
                log.warn(
                    "Auditoria de LOGIN_FALLIDO respondio con status={} email={} ip={}",
                    respuesta.getStatusCode(),
                    afectado,
                    ip
                );
            }

        } catch (Exception e) {
            // El login debe seguir siendo rechazado aunque ms-cumplimiento
            // no esté disponible. Conservamos evidencia local del fallo.
            log.warn(
                "No fue posible registrar LOGIN_FALLIDO en ms-cumplimiento email={} ip={} motivo={}",
                afectado,
                ip,
                e.getMessage()
            );
        }
    }

    private record AuditoriaLoginRequest(
        String tipoAccion,
        String administradorId,
        String afectado,
        String valorAnterior,
        String valorNuevo,
        String motivo,
        String ipOrigen
    ) {
    }
}
