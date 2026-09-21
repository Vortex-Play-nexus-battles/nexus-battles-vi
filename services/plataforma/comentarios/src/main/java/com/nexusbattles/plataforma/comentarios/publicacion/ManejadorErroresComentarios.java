package com.nexusbattles.plataforma.comentarios.publicacion;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios.MotivoDeRechazo;

@RestControllerAdvice
public class ManejadorErroresComentarios {

    @ExceptionHandler(HiloDeComentarios.PublicacionRechazada.class)
    public ProblemDetail manejarPublicacionRechazada(
            HiloDeComentarios.PublicacionRechazada ex) {

        HttpStatus estado = switch (ex.motivo()) {
            case AUTOR_SILENCIADO -> HttpStatus.FORBIDDEN;
            case CALIFICACION_DUPLICADA -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, ex.getMessage());
        problema.setProperty("motivo", ex.motivo().name());
        return problema;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail manejarCarreraDeCalificacion(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "otra solicitud simultanea ya guardo la calificacion de este autor"
                        + " para este producto; reintenta sin calificacion");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail manejarSolicitudInvalida(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** RF-USR-004 (HU-COM-001, CA-03): sin poder comprobar la sancion no se publica. */
    @ExceptionHandler(SancionesNoDisponibles.class)
    public ProblemDetail manejarSancionesNoDisponibles(SancionesNoDisponibles ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problema.setType(java.net.URI.create("https://nexusbattles.local/errores/sanciones-no-disponibles"));
        problema.setTitle("No se pudo comprobar tu estado para publicar");
        return problema;
    }
}
