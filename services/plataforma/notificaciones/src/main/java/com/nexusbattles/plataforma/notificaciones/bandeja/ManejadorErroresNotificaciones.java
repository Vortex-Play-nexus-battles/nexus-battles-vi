package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.sql.SQLException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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

    /**
     * Carrera entre dos reintentos del mismo evento: los dos pasan la
     * verificacion previa y el segundo choca con la restriccion unica al
     * confirmar. Es el mismo caso que AvisoDuplicado, solo que decidido por
     * la base, asi que responde lo mismo.
     *
     * <p>Solo una clave repetida es un duplicado. Auditoria de DEV del 30-sep:
     * cualquier otra violacion —un identificador que no cabia en su columna—
     * tambien salia como 409, y el emisor, que con razon lee el 409 como «ya
     * estaba», daba el aviso por entregado sin que existiera. Lo demas es un
     * dato que la bandeja no puede guardar: 400.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail manejarDuplicadoEnCarrera(DataIntegrityViolationException ex) {
        if (esClaveRepetida(ex)) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    "el evento ya habia sido recibido por otra solicitud simultanea");
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "los datos del evento no caben en la bandeja");
    }

    /** SQLSTATE de PostgreSQL para una restriccion unica violada. */
    static final String CLAVE_REPETIDA = "23505";

    /**
     * Si en la cadena de causas hay una clave unica repetida. Publico desde
     * HU-NOT-001: el importador de avisos del catalogo distingue con esto la
     * carrera de dos sesiones (un repetido, se ignora) de otro fallo.
     */
    public static boolean esClaveRepetida(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause() == causa ? null : causa.getCause()) {
            if (causa instanceof DuplicateKeyException) {
                return true;
            }
            if (causa instanceof SQLException sql && CLAVE_REPETIDA.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail manejarSolicitudInvalida(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
