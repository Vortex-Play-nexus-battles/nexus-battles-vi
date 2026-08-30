package com.nexusbattles.ms_cumplimiento.auditoria.controller;

import com.nexusbattles.ms_cumplimiento.auditoria.dto.AuditLogResponse;
import com.nexusbattles.ms_cumplimiento.auditoria.dto.RegistrarAuditoriaRequest;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import com.nexusbattles.ms_cumplimiento.auditoria.security.RequireSuperAdmin2FA;
import com.nexusbattles.ms_cumplimiento.auditoria.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/auditoria")
public class AuditLogController {

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