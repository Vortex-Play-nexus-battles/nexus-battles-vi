package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.plataforma.correo.envio.ClasificadorDeFallos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * La cola no esta disponible: 503 en formato problem details (regla 4).
 *
 * <p>Es la otra mitad de «202 = guardado»: si la fila no se pudo escribir, la
 * respuesta NO puede ser 202. Quien pidio el correo recibe un error que puede
 * reintentar (ms-identidad lo hace con Resilience4j) en vez de un acuse de
 * algo que nadie va a enviar, que es exactamente lo que pasaba antes de B1.
 *
 * <p>Solo los fallos de disponibilidad de la base (conexion, tiempo de
 * espera, transaccion que no arranca). Un error de otro tipo es un defecto
 * del servicio y sigue siendo un 500.
 */
@RestControllerAdvice
public class ManejadorDeErroresDeCorreo {

    /** Misma raiz que el resto de errores de la plataforma. */
    static final URI COLA_NO_DISPONIBLE =
            URI.create("https://nexusbattles.local/errores/cola-de-correo-no-disponible");

    /** Segundos que se sugieren antes de reintentar (cabecera Retry-After). */
    static final String REINTENTAR_EN_SEGUNDOS = "5";

    private static final Logger BITACORA = LoggerFactory.getLogger(ManejadorDeErroresDeCorreo.class);

    @ExceptionHandler({
            DataAccessResourceFailureException.class,
            TransientDataAccessException.class,
            RecoverableDataAccessException.class,
            CannotCreateTransactionException.class
    })
    public ResponseEntity<ProblemDetail> colaNoDisponible(Exception error) {
        BITACORA.warn("La cola de correo no esta disponible; se responde 503 sin encolar nada: {}",
                ClasificadorDeFallos.resumen(error));
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo guardar el correo en la cola persistente, así que no se enviará. "
                        + "Vuelve a pedirlo en unos segundos.");
        problema.setType(COLA_NO_DISPONIBLE);
        problema.setTitle("La cola de correo no está disponible");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, REINTENTAR_EN_SEGUNDOS)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }
}
