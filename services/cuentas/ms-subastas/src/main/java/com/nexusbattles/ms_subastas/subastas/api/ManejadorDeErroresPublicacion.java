package com.nexusbattles.ms_subastas.subastas.api;

import com.nexusbattles.ms_subastas.seguridad.TokenInvalidoException;
import com.nexusbattles.ms_subastas.subastas.port.*;
import com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PublicacionSubastaController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorDeErroresPublicacion {
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ProblemDetail integridad(org.springframework.dao.DataIntegrityViolationException ex) {
        org.slf4j.LoggerFactory.getLogger(ManejadorDeErroresPublicacion.class)
                .error("Fallo tecnico de integridad al publicar", ex);
        return ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo guardar la subasta");
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ProblemDetail jsonInvalido(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.BAD_REQUEST, "Solicitud JSON invalida");
    }
    @ExceptionHandler(TokenInvalidoException.class)
    public ProblemDetail token(TokenInvalidoException ex) {
        return ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(401), "Se requiere un JWT valido con uid para publicar");
    }

    @ExceptionHandler(PublicacionSubastaException.class)
    public ProblemDetail publicacion(PublicacionSubastaException ex) {
        int estado = switch (ex.getMotivo()) {
            case SOLICITUD_INVALIDA -> 400;
            case NO_AUTENTICADO -> 401;
            case PROHIBIDO -> 403;
            case NO_ENCONTRADO -> 404;
            case CONFLICTO -> 409;
            case REGLA_NEGOCIO -> 422;
            case DEPENDENCIA_NO_DISPONIBLE -> 503;
        };
        return ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(estado), ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail solicitud(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(422), ex.getMessage());
    }

    @ExceptionHandler({SancionesClientException.class, CatalogoProductosClientException.class, InventarioClientException.class})
    public ProblemDetail dependencia(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(503), "No se pudo consultar una dependencia de publicacion");
    }
}
