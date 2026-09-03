package nexus.api;

import nexus.dominio.HeroeNoDisponibleException;
import nexus.dominio.NivelFueraDeRangoException;
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
        return problema(HttpStatus.NOT_FOUND, "Héroe no disponible", e.getMessage());
    }

    @ExceptionHandler(NivelFueraDeRangoException.class)
    public ProblemDetail nivelNoValido(NivelFueraDeRangoException e) {
        return problema(HttpStatus.BAD_REQUEST, "Nivel no válido", e.getMessage());
    }

    /** Reglas del dominio violadas por la entrada (puntos negativos, dado fuera de 1..8). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail entradaNoValida(IllegalArgumentException e) {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud no válida", e.getMessage());
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
