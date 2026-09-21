package com.nexusbattles.plataforma.adminparametros.parametros;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Regla 4: problem details con `motivo`. */
@RestControllerAdvice(basePackages = "com.nexusbattles.plataforma.adminparametros")
public class ManejadorErroresParametros {

    static final String BASE = "https://nexusbattles.local/errores/";

    @ExceptionHandler(ParametroRechazado.class)
    public ProblemDetail manejar(ParametroRechazado ex) {
        HttpStatus estado = switch (ex.motivo()) {
            case PERMISO_INSUFICIENTE -> HttpStatus.FORBIDDEN;
            case NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case SOLICITUD_INVALIDA, VALOR_INVALIDO -> HttpStatus.BAD_REQUEST;
            case INALTERABLE -> HttpStatus.CONFLICT;
        };
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, ex.getMessage());
        problema.setType(URI.create(BASE + ex.motivo().name().toLowerCase().replace('_', '-')));
        problema.setTitle(switch (ex.motivo()) {
            case PERMISO_INSUFICIENTE -> "No tienes permiso para esto";
            case NO_ENCONTRADO -> "Parametro no encontrado";
            case SOLICITUD_INVALIDA -> "Solicitud invalida";
            case VALOR_INVALIDO -> "Valor fuera de rango o de tipo distinto";
            case INALTERABLE -> "Parametro inalterable";
        });
        problema.setProperty("motivo", ex.motivo().name());
        return problema;
    }
}
