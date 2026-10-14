package com.nexusbattles.ms_identidad.rbac.service;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaNoDisponibleException;
import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class AuditoriaCambioRolClient {

    private final RestClient restClient;
    private final String urlAuditoria;

    /**
     * @param urlAuditoria misma propiedad y mismo valor por defecto que
     *                     {@code AuditoriaClient}: el puerto real de
     *                     ms-cumplimiento es 8091, no 8080
     * @param credencial   credencial de servicio de ms-identidad (ADR-005), la
     *                     misma que llevan los demas clientes salientes
     */
    public AuditoriaCambioRolClient(
        @Value("${app.auditoria.url:http://localhost:8091/api/v1/admin/auditoria/eventos}")
        String urlAuditoria,
        CredencialPropia credencial) {

        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(1000);

        this.restClient = RestClient.builder()
            .requestFactory(requestFactory)
            .requestInterceptor(credencial)
            .build();

        this.urlAuditoria = urlAuditoria;
    }

    /**
     * Registra de forma síncrona el cambio de rol.
     *
     * HU-RBAC-003 / HU-AUD-001:
     * si la auditoría no responde correctamente, se lanza una excepción
     * y el cambio administrativo NO debe continuar.
     */
    public void registrarCambioRol(
        String administradorId,
        Long usuarioAfectadoId,
        Role rolAnterior,
        Role nuevoRol,
        String ipOrigen) {
        registrarCambioRol(administradorId, usuarioAfectadoId.toString(), rolAnterior, nuevoRol, null, ipOrigen);
    }

    /**
     * Como el anterior, con la cuenta afectada por su {@code uid} (el mismo
     * identificador que el resto de la auditoria) y el motivo que escribio el
     * Super Administrador (7.3.5: «motivo o justificacion de la accion»).
     *
     * @throws AuditoriaNoDisponibleException si ms-cumplimiento no lo registra (503)
     */
    public void registrarCambioRol(
        String administradorId,
        String afectado,
        Role rolAnterior,
        Role nuevoRol,
        String motivo,
        String ipOrigen) {

        Map<String, String> solicitud = Map.of(
            "tipoAccion", "CAMBIO_ROL",
            "administradorId", administradorId,
            "afectado", afectado,
            "valorAnterior", rolAnterior.name(),
            "valorNuevo", nuevoRol.name(),
            "motivo", motivo == null || motivo.isBlank() ? "Asignación administrativa de rol" : motivo.strip(),
            "ipOrigen", ipOrigen == null || ipOrigen.isBlank() ? "DESCONOCIDA" : ipOrigen
        );

        ResponseEntity<Void> respuesta;
        try {
            respuesta = restClient.post()
                .uri(urlAuditoria)
                .contentType(MediaType.APPLICATION_JSON)
                .body(solicitud)
                .retrieve()
                .toBodilessEntity();
        } catch (RuntimeException sinRespuesta) {
            throw new AuditoriaNoDisponibleException(
                "No se cambió el rol: el registro de auditoría no respondió. Inténtalo de nuevo en unos segundos.");
        }

        if (respuesta.getStatusCode() != HttpStatus.CREATED) {
            throw new AuditoriaNoDisponibleException(
                "No se cambió el rol: el registro de auditoría no lo aceptó."
            );
        }
    }
}
