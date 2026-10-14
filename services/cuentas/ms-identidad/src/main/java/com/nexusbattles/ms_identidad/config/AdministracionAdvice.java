package com.nexusbattles.ms_identidad.config;

import com.nexusbattles.ms_identidad.admin.service.JerarquiaInsuficienteException;
import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaNoDisponibleException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Dos rechazos del panel de administracion con el mismo formato en todas sus
 * rutas (regla 4, problem details):
 *
 * <ul>
 *   <li>403 {@code jerarquia-insuficiente}: la accion esta permitida para el
 *       rol de quien actua, pero la cuenta afectada tiene un rol igual o
 *       superior (7.3.1).</li>
 *   <li>503 {@code auditoria-no-disponible}: la operacion exige quedar en el
 *       registro de auditoria y ms-cumplimiento no respondio; no se aplico
 *       nada (fail-closed). Con {@code Retry-After}.</li>
 * </ul>
 */
@RestControllerAdvice
public class AdministracionAdvice {

    public static final URI TIPO_JERARQUIA =
            URI.create("https://nexusbattles.upb.edu.co/errors/jerarquia-insuficiente");
    public static final URI TIPO_AUDITORIA =
            URI.create("https://nexusbattles.upb.edu.co/errors/auditoria-no-disponible");

    @ExceptionHandler(JerarquiaInsuficienteException.class)
    public ResponseEntity<ProblemDetail> jerarquia(JerarquiaInsuficienteException error, HttpServletRequest peticion) {
        return problema(HttpStatus.FORBIDDEN, TIPO_JERARQUIA, "Rol insuficiente sobre esta cuenta",
                error.getMessage(), peticion.getRequestURI(), null);
    }

    @ExceptionHandler(AuditoriaNoDisponibleException.class)
    public ResponseEntity<ProblemDetail> auditoria(AuditoriaNoDisponibleException error, HttpServletRequest peticion) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, TIPO_AUDITORIA, "Auditoría no disponible",
                error.getMessage(), peticion.getRequestURI(), AuditoriaNoDisponibleException.REINTENTAR_EN_SEGUNDOS);
    }

    static ResponseEntity<ProblemDetail> problema(HttpStatus estado, URI tipo, String titulo, String detalle,
                                                  String ruta, Integer reintentarEn) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(tipo);
        problema.setTitle(titulo);
        if (ruta != null) {
            problema.setInstance(URI.create(ruta));
        }
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(estado)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (reintentarEn != null) {
            respuesta.header(HttpHeaders.RETRY_AFTER, String.valueOf(reintentarEn));
        }
        return respuesta.body(problema);
    }
}
