package com.nexusbattles.ms_identidad.auth.recuperacion;

import com.nexusbattles.ms_identidad.auth.codigos.CodigoInvalidoException;
import com.nexusbattles.ms_identidad.auth.codigos.DemasiadosIntentosException;
import com.nexusbattles.ms_identidad.auth.codigos.IpDelCliente;
import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import com.nexusbattles.ms_identidad.auth.dto.CanjearTokenRequest;
import com.nexusbattles.ms_identidad.auth.dto.SolicitarRestablecimientoRequest;
import com.nexusbattles.ms_identidad.auth.verificacion.CodigoDeCorreoRequest;
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
 * {@code /api/v1/auth/restablecer} — HU-COR-003 endurecida (B1) y el canje de
 * la activacion de cuentas administrativas. Rutas publicas.
 *
 * <p>Los dos 200 siguen siendo el texto plano de siempre (contrato); los
 * errores, desde 2.0.0, problem details con el {@code type} del contrato.
 */
@RestController
@RequestMapping("/api/v1/auth/restablecer")
public class RecuperacionController {

    private final RecuperacionService servicio;

    public RecuperacionController(RecuperacionService servicio) {
        this.servicio = servicio;
    }

    /** Siempre 200 y el mismo texto, exista o no la cuenta (anti-enumeracion). */
    @PostMapping("/solicitar")
    public ResponseEntity<String> solicitar(@Valid @RequestBody SolicitarRestablecimientoRequest datos) {
        servicio.solicitar(datos.getEmail());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(RecuperacionService.MENSAJE_SOLICITUD);
    }

    @PostMapping("/preguntas")
    public ResponseEntity<PreguntasDeRecuperacion> preguntas(@Valid @RequestBody CodigoDeCorreoRequest datos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.preguntas(datos.email(), datos.codigo()));
    }

    @PostMapping("/confirmar")
    public ResponseEntity<String> confirmar(@Valid @RequestBody CanjearTokenRequest datos,
                                            HttpServletRequest peticion) {
        servicio.confirmar(datos.getEmail(), datos.getCodigo(), datos.getRespuestas(), datos.getNuevaPassword(),
                IpDelCliente.de(peticion));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(RecuperacionService.MENSAJE_CANJE);
    }

    @ExceptionHandler(CodigoInvalidoException.class)
    public ResponseEntity<ProblemDetail> codigoInvalido(HttpServletRequest peticion) {
        return Problemas.codigoInvalido(peticion.getRequestURI());
    }

    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<ProblemDetail> demasiadosIntentos(HttpServletRequest peticion) {
        return Problemas.demasiadosIntentos(peticion.getRequestURI());
    }

    @ExceptionHandler(RecuperacionRechazadaException.class)
    public ResponseEntity<ProblemDetail> rechazada(RecuperacionRechazadaException rechazo,
                                                   HttpServletRequest peticion) {
        return Problemas.de(HttpStatus.valueOf(rechazo.getMotivo().estado()), rechazo.getMotivo().tipo(),
                rechazo.getMotivo().titulo(), rechazo.getMessage(), peticion.getRequestURI());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> malFormada(HttpServletRequest peticion) {
        return Problemas.datosInvalidos(peticion.getRequestURI());
    }
}
