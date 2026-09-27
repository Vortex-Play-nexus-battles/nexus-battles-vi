package com.nexusbattles.ms_identidad.config;

import com.nexusbattles.ms_identidad.auth.validation.ModeracionNoDisponibleException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * 503 {@code moderacion-no-disponible}, igual en todas las rutas que
 * dependen de moderacion-sanciones (B2): registro, alta administrativa,
 * cambio de apodo (perfil propio y administracion) y las sanciones del panel.
 *
 * <p>Siempre problem details (regla 4) y siempre con {@code Retry-After}: es
 * un fallo transitorio de otro servicio, no un dato invalido de la persona, y
 * la interfaz tiene que poder decir «vuelve a intentarlo» sin interpretar un
 * texto.
 */
@RestControllerAdvice
public class ModeracionNoDisponibleAdvice {

    public static final URI TIPO = URI.create("https://nexusbattles.upb.edu.co/errors/moderacion-no-disponible");

    @ExceptionHandler(ModeracionNoDisponibleException.class)
    public ResponseEntity<ProblemDetail> noDisponible(ModeracionNoDisponibleException error,
                                                      HttpServletRequest peticion) {
        return respuesta(error.getMessage(), peticion.getRequestURI());
    }

    public static ResponseEntity<ProblemDetail> respuesta(String detalle, String ruta) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detalle);
        problema.setType(TIPO);
        problema.setTitle("Moderación no disponible");
        if (ruta != null) {
            problema.setInstance(URI.create(ruta));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ModeracionNoDisponibleException.REINTENTAR_EN_SEGUNDOS))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }
}
