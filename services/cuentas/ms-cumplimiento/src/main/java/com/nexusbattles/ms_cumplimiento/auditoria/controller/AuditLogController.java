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
import java.util.List;
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

    /**
     * 1.3.0: {@code administradorId} se puede repetir, como {@code afectado}:
     * la misma persona figura con su apodo o con su {@code uid} segun el
     * servicio que registro la accion.
     */
    @GetMapping
    @RequireSuperAdmin2FA
    public Page<AuditLogResponse> consultar(
        @RequestParam(required = false) List<String> administradorId,
        @RequestParam(required = false) List<String> afectado,
        @RequestParam(required = false) AuditActionType tipoAccion,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
        Pageable pageable) {

        Page<AuditLog> resultado = auditLogService.buscar(
            new AuditLogService.FiltroDeAuditoria(conValores(administradorId), conValores(afectado), tipoAccion,
                desde, hasta), pageable);
        return resultado.map(AuditLogResponse::from);
    }

    /**
     * Exporta el registro de auditoría filtrado (HU-AUD-004), en PDF o, desde
     * 1.3.0, en CSV ({@code formato=csv}) para analizarlo en una hoja de
     * cálculo.
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
        @RequestParam(required = false) List<String> administradorId,
        @RequestParam(required = false) List<String> afectado,
        @RequestParam(required = false) AuditActionType tipoAccion,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
        @RequestParam(required = false) String formato,
        Authentication autenticacion,
        HttpServletRequest request) {

        AuditLogService.Formato pedido = AuditLogService.Formato.de(formato);
        String administradorQueExporta = autenticacion != null ? autenticacion.getName() : "desconocido";
        String ipOrigen = ipDeOrigen(request);

        AuditLogService.Exportacion exportacion = auditLogService.exportar(
            new AuditLogService.FiltroDeAuditoria(conValores(administradorId), conValores(afectado), tipoAccion,
                desde, hasta),
            pedido, administradorQueExporta, ipOrigen);

        String nombreArchivo = "auditoria-" + FORMATO_NOMBRE_ARCHIVO.format(Instant.now()
            .atZone(java.time.ZoneOffset.UTC)) + "." + pedido.extension();

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(pedido.tipoDeContenido()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(nombreArchivo).build().toString())
            .body(exportacion.contenido());
    }

    /**
     * La IP de quien exporta: la que fija el borde ({@code X-Real-IP}, que
     * nginx sobrescribe siempre), luego la primera de {@code X-Forwarded-For}
     * y, sin borde, la del socket.
     */
    private String ipDeOrigen(HttpServletRequest request) {
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.strip();
        }
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
     *
     * <p>201 con la entrada nueva; 200 con la que ya existía cuando el
     * evento trae una {@code referencia} ya registrada (1.3.0): el origen
     * reintenta sin miedo a duplicar.
     */
    @PostMapping("/eventos")
    public ResponseEntity<AuditLogResponse> registrarEvento(@RequestBody RegistrarAuditoriaRequest solicitud) {
        AuditLogService.ResultadoDeRegistro resultado = auditLogService.registrarEvento(solicitud);
        return ResponseEntity.status(resultado.nueva() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(AuditLogResponse.from(resultado.entrada()));
    }

    /** {@code afectado} sin repetir ni valores vacios; sin el parametro, ninguno. */
    static List<String> conValores(List<String> afectado) {
        return afectado == null ? List.of() : afectado.stream()
            .filter(v -> v != null && !v.isBlank())
            .map(String::strip)
            .distinct()
            .toList();
    }
}
