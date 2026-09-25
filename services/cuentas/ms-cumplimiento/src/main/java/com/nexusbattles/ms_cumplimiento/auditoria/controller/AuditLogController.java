package com.nexusbattles.ms_cumplimiento.auditoria.controller;

import com.nexusbattles.ms_cumplimiento.auditoria.dto.AuditLogResponse;
import com.nexusbattles.ms_cumplimiento.auditoria.dto.RegistrarAuditoriaRequest;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import com.nexusbattles.ms_cumplimiento.auditoria.security.RequireSuperAdmin2FA;
import com.nexusbattles.ms_cumplimiento.auditoria.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/admin/auditoria")
@CrossOrigin(origins = "*")
public class AuditLogController {

    private static final DateTimeFormatter FORMATO_NOMBRE_ARCHIVO =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @RequireSuperAdmin2FA
    public Page<AuditLogResponse> consultar(
        @RequestParam(required = false) String administradorId,
        @RequestParam(required = false) AuditActionType tipoAccion,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
        Pageable pageable) {

        Page<AuditLog> resultado = auditLogService.consultar(
            administradorId, tipoAccion, desde, hasta, pageable);
        return resultado.map(AuditLogResponse::from);
    }

    /**
     * Exporta a PDF el registro de auditoría filtrado (HU-AUD-004).
     *
     * <p>Mismos filtros y misma protección ({@code @RequireSuperAdmin2FA})
     * que {@link #consultar}. Si el volumen filtrado supera el máximo por
     * operación, responde 422 (ver {@code ManejadorDeErrores}) en vez de
     * entregar un archivo incompleto — el administrador debe acotar el
     * rango y reintentar.
     *
     * <p>El administrador que exporta y la IP de origen se leen del propio
     * request (principal del token, {@code X-Forwarded-For}/IP remota),
     * nunca del cuerpo: así el registro de "quién exportó" no se puede
     * falsear desde el cliente.
     */
    @GetMapping("/exportar")
    @RequireSuperAdmin2FA
    public ResponseEntity<byte[]> exportar(
        @RequestParam(required = false) String administradorId,
        @RequestParam(required = false) AuditActionType tipoAccion,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
        Authentication autenticacion,
        HttpServletRequest request) {

        String administradorQueExporta = autenticacion != null ? autenticacion.getName() : "desconocido";
        String ipOrigen = ipDeOrigen(request);

        byte[] pdf = auditLogService.exportarPdf(
            administradorId, tipoAccion, desde, hasta, administradorQueExporta, ipOrigen);

        String nombreArchivo = "auditoria-" + FORMATO_NOMBRE_ARCHIVO.format(Instant.now()
            .atZone(java.time.ZoneOffset.UTC)) + ".pdf";

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(nombreArchivo).build().toString())
            .body(pdf);
    }

    private String ipDeOrigen(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Endpoint para que otros microservicios (ms-identidad, etc.) registren
     * eventos de auditoría por HTTP, sin importar clases Java internas de
     * ms-cumplimiento.
     */
    @PostMapping("/eventos")
    @ResponseStatus(HttpStatus.CREATED)
    public AuditLogResponse registrarEvento(@RequestBody RegistrarAuditoriaRequest solicitud) {
        AuditLog registrado = auditLogService.registrarDesdeSolicitud(solicitud);
        return AuditLogResponse.from(registrado);
    }
}
