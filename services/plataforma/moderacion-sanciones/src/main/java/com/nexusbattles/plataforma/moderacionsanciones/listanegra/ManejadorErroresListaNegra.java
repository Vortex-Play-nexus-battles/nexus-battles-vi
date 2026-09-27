package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Problem details (regla 4) de la lista negra, con el mismo esquema de
 * {@code type} que las sanciones ({@code https://nexusbattles.local/errores/...})
 * y el motivo repetido en mayusculas para que el cliente decida sin leer texto.
 *
 * <p>Sigue sin {@code basePackages}, como antes de B2: el {@code 400} por
 * {@link IllegalArgumentException} lo recibian tambien las rutas de sanciones
 * (un token sin {@code uid} en una ruta de jugador) y quitarselo las dejaria en
 * 500.
 */
@RestControllerAdvice
public class ManejadorErroresListaNegra {

    static final String BASE = "https://nexusbattles.local/errores/";

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail manejarSolicitudInvalida(IllegalArgumentException ex) {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud invalida", ex.getMessage(), "SOLICITUD_INVALIDA");
    }

    @ExceptionHandler(TerminoNoEncontradoException.class)
    public ProblemDetail manejarTerminoNoEncontrado(TerminoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Termino no encontrado", ex.getMessage(), "TERMINO_NO_ENCONTRADO");
    }

    @ExceptionHandler(TerminoDuplicadoException.class)
    public ProblemDetail manejarTerminoDuplicado(TerminoDuplicadoException ex) {
        return problema(HttpStatus.CONFLICT, "Termino duplicado", ex.getMessage(), "TERMINO_DUPLICADO");
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle, String motivo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create(BASE + motivo.toLowerCase().replace('_', '-')));
        problema.setTitle(titulo);
        problema.setProperty("motivo", motivo);
        return problema;
    }
}
