package com.nexusbattles.ms_cumplimiento.auditoria.controller;

import com.nexusbattles.ms_cumplimiento.auditoria.exception.AuditWriteException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores en formato problem details (regla 4), identicos al resto de la
 * plataforma.
 *
 * <p>Sin esto, un {@code tipoAccion} desconocido salia como 500 (el
 * {@code IllegalArgumentException} del servicio nadie lo traducia) y el
 * rechazo del aspecto de superadministrador dependia de que la cadena de
 * seguridad lo atrapara por fuera del controlador.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail solicitudInvalida(IllegalArgumentException error) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
        problema.setTitle("Solicitud invalida");
        return problema;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accesoDenegado(AccessDeniedException error) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.getMessage());
        problema.setTitle("Acceso denegado");
        return problema;
    }

    @ExceptionHandler(AuditWriteException.class)
    public ProblemDetail bitacoraNoDisponible(AuditWriteException error) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage());
        problema.setTitle("Bitacora de auditoria no disponible");
        return problema;
    }
}
