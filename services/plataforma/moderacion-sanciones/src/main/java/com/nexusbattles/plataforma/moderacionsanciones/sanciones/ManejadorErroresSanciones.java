package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Problem details (regla 4) de las sanciones, con {@code type} estable por motivo. */
@RestControllerAdvice(basePackages = "com.nexusbattles.plataforma.moderacionsanciones.sanciones")
public class ManejadorErroresSanciones {

    static final String BASE = "https://nexusbattles.local/errores/";

    @ExceptionHandler(SancionRechazada.class)
    public ProblemDetail manejar(SancionRechazada ex) {
        HttpStatus estado = switch (ex.motivo()) {
            case PERMISO_INSUFICIENTE -> HttpStatus.FORBIDDEN;
            case NO_ENCONTRADA -> HttpStatus.NOT_FOUND;
            case USUARIO_BANEADO, APELACION_RESUELTA -> HttpStatus.CONFLICT;
            case APELACION_NO_PROCEDE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case SOLICITUD_INVALIDA -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, ex.getMessage());
        problema.setType(URI.create(BASE + ex.motivo().name().toLowerCase().replace('_', '-')));
        problema.setTitle(switch (ex.motivo()) {
            case PERMISO_INSUFICIENTE -> "No tienes permiso para esto";
            case NO_ENCONTRADA -> "No encontrada";
            case USUARIO_BANEADO -> "El usuario ya esta baneado";
            case APELACION_RESUELTA -> "La apelacion ya esta resuelta";
            case APELACION_NO_PROCEDE -> "La apelacion no procede";
            case SOLICITUD_INVALIDA -> "Solicitud invalida";
        });
        problema.setProperty("motivo", ex.motivo().name());
        return problema;
    }
}
