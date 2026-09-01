package com.nexusbattles.plataforma.notificaciones.bandeja;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los fallos del dominio al formato de error estandar de la plataforma
 * (problem details), igual en los veinte modulos.
 */
@RestControllerAdvice
public class ManejadorErroresNotificaciones {

    @ExceptionHandler(AvisoNoEncontrado.class)
    public ProblemDetail manejarNoEncontrado(AvisoNoEncontrado ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AvisoDuplicado.class)
    public ProblemDetail manejarDuplicado(AvisoDuplicado ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail manejarSolicitudInvalida(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
