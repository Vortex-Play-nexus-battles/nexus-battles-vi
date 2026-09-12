package com.nexusbattles.plataforma.resiliencia;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Convierte una {@link DependenciaDegradada} en el problem detail estandar
 * (HU-DIS-003, CA-02).
 *
 * <p>Esta aqui y no en cada servicio para que la respuesta sea identica en los
 * veinte modulos (regla 4). Un servicio que declara esta biblioteca obtiene el
 * comportamiento correcto sin escribir un manejador propio: lanza
 * {@code DependenciaDegradada} y ya.
 */
@RestControllerAdvice
public class ManejadorDeDegradacion {

    private final long reintentarEnSegundos;

    public ManejadorDeDegradacion(long reintentarEnSegundos) {
        this.reintentarEnSegundos = reintentarEnSegundos;
    }

    @ExceptionHandler(DependenciaDegradada.class)
    public ResponseEntity<ProblemDetail> seccionNoDisponible(DependenciaDegradada degradada) {
        ProblemDetail problema = ErroresDeDegradacion.problema(degradada, reintentarEnSegundos);

        // Retry-After ademas del cuerpo: los clientes automaticos y los
        // intermediarios lo leen de la cabecera, no del JSON.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(reintentarEnSegundos))
                .body(problema);
    }
}
