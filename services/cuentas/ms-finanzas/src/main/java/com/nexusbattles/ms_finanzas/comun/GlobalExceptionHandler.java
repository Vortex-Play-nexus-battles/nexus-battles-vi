package com.nexusbattles.ms_finanzas.comun;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.nexusbattles.ms_finanzas.transacciones.TransaccionYaRegistradaException;

/**
 * Traduce las excepciones del servicio a respuestas RFC 7807 (regla 4 de
 * plataforma). {@code spring.mvc.problem-details.enabled=true} ya activa el
 * formato Problem Details a nivel Spring; este handler solo se encarga de
 * las excepciones propias del dominio y de darles un {@code type} URI
 * consistente con la convención del proyecto.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String BASE_TYPE = "https://nexusbattles.upb.edu.co/errors/";

    @ExceptionHandler(TransaccionYaRegistradaException.class)
    public ProblemDetail manejarTransaccionDuplicada(TransaccionYaRegistradaException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problema.setType(URI.create(BASE_TYPE + "transaccion-ya-registrada"));
        problema.setTitle("Transacción ya registrada");
        problema.setProperty("refId", ex.getRefId());
        return problema;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail manejarArgumentoInvalido(IllegalArgumentException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        problema.setType(URI.create(BASE_TYPE + "argumento-invalido"));
        problema.setTitle("Argumento inválido");
        return problema;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarInesperada(Exception ex) {
        // Se registra la excepción real en el log pero al cliente se le
        // devuelve un mensaje genérico — no queremos filtrar detalles de
        // implementación en el cuerpo de la respuesta.
        log.error("Error no controlado en ms-finanzas", ex);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Inténtalo de nuevo más tarde.");
        problema.setType(URI.create(BASE_TYPE + "error-interno"));
        problema.setTitle("Error interno del servidor");
        return problema;
    }
}
