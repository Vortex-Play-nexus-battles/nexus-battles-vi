package com.nexusbattles.ms_identidad.sanciones.apelacion;

import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import com.nexusbattles.ms_identidad.auth.validation.ModeracionNoDisponibleException;
import com.nexusbattles.ms_identidad.config.ModeracionNoDisponibleAdvice;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient.ApelacionDeLaCuenta;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient.EstadoDeApelacion;
import com.nexusbattles.ms_identidad.sanciones.SancionRechazadaException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/auth/apelacion} (ms-identidad-auth.yaml 2.4.0) — la puerta
 * que tiene una cuenta suspendida o baneada para apelar sin sesion. Sin
 * {@code @RequirePermission}: no hay sesion que pedir; lo que autoriza es el
 * codigo que el login le dio tras comprobar su contrasena. Ver
 * {@link ApelacionSinSesionService}.
 *
 * <p>Las dos son POST para que el codigo nunca vaya en la direccion (ni en el
 * historial del navegador ni en las bitacoras del borde), y las respuestas
 * no se guardan en cache.
 */
@RestController
@RequestMapping("/api/v1/auth/apelacion")
public class ApelacionSinSesionController {

    private final ApelacionSinSesionService servicio;

    public ApelacionSinSesionController(ApelacionSinSesionService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/consulta")
    public ResponseEntity<EstadoDeApelacion> consultar(@Valid @RequestBody CodigoDeApelacionRequest datos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.consultar(datos.codigo()));
    }

    @PostMapping
    public ResponseEntity<ApelacionDeLaCuenta> apelar(@Valid @RequestBody ApelacionSinSesionRequest datos) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(servicio.apelar(datos.codigo(), datos.sancionId(), datos.argumento()));
    }

    @ExceptionHandler(CodigoDeApelacionInvalidoException.class)
    public ResponseEntity<ProblemDetail> codigoInvalido(CodigoDeApelacionInvalidoException error,
                                                        HttpServletRequest peticion) {
        return Problemas.de(HttpStatus.UNAUTHORIZED, "codigo-de-apelacion-invalido", "Código para apelar no válido",
                error.getMessage(), peticion.getRequestURI());
    }

    /**
     * Moderacion decidio que no procede (422), que la sancion no existe (404)
     * o que la peticion no vale (400): el mismo estado, con su detalle.
     */
    @ExceptionHandler(SancionRechazadaException.class)
    public ResponseEntity<ProblemDetail> noProcede(SancionRechazadaException rechazo, HttpServletRequest peticion) {
        HttpStatus estado = HttpStatus.resolve(rechazo.getEstado());
        if (estado == null || !estado.is4xxClientError() || estado == HttpStatus.UNAUTHORIZED
                || estado == HttpStatus.FORBIDDEN) {
            // Un 401/403 de moderacion es un fallo de la credencial de ESTE
            // servicio, no de la persona: se dice como lo que es.
            return ModeracionNoDisponibleAdvice.respuesta(NO_REGISTRADA, peticion.getRequestURI());
        }
        return Problemas.de(estado, "apelacion-no-procede", "La apelación no procede", rechazo.getMessage(),
                peticion.getRequestURI());
    }

    @ExceptionHandler(ModeracionNoDisponibleException.class)
    public ResponseEntity<ProblemDetail> noDisponible(HttpServletRequest peticion) {
        return ModeracionNoDisponibleAdvice.respuesta(NO_REGISTRADA, peticion.getRequestURI());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> malFormada(HttpServletRequest peticion) {
        return Problemas.datosInvalidos(peticion.getRequestURI());
    }

    static final String NO_REGISTRADA =
            "El servicio de moderación no responde: tu apelación no se registró. Inténtalo de nuevo en unos segundos.";
}
