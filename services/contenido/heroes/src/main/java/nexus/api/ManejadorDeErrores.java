package nexus.api;

import nexus.dominio.HeroeNoDisponibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Formato de error estandar de la plataforma: detalles de problema (RFC 9457),
 * identico en todos los modulos. El campo detail lleva el mensaje apto para el
 * usuario final — la interfaz lo muestra tal cual, nunca el codigo de estado
 * (regla del cliente, acta 2026-08-13).
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    @ExceptionHandler(HeroeNoDisponibleException.class)
    public ProblemDetail heroeNoDisponible(HeroeNoDisponibleException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problema.setTitle("Héroe no disponible");
        return problema;
    }
}
