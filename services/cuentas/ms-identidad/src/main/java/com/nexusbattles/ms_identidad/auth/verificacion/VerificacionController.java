package com.nexusbattles.ms_identidad.auth.verificacion;

import com.nexusbattles.ms_identidad.auth.codigos.CodigoInvalidoException;
import com.nexusbattles.ms_identidad.auth.codigos.DemasiadosIntentosException;
import com.nexusbattles.ms_identidad.auth.codigos.IpDelCliente;
import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
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
 * {@code /api/v1/auth/verificacion} — confirmar el correo de un autorregistro
 * y pedir otro codigo (B1, ms-identidad-auth.yaml 2.x). Rutas publicas: quien
 * las usa todavia no puede iniciar sesion, esa es la situacion. El limite por
 * direccion IP lo pone el borde; aqui, el de la cuenta.
 */
@RestController
@RequestMapping("/api/v1/auth/verificacion")
public class VerificacionController {

    private final VerificacionDeCorreoService servicio;

    public VerificacionController(VerificacionDeCorreoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/confirmacion")
    public ResponseEntity<VerificacionResponse> confirmar(@Valid @RequestBody CodigoDeCorreoRequest datos,
                                                          HttpServletRequest peticion) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.confirmar(datos.email(), datos.codigo(), IpDelCliente.de(peticion)));
    }

    /** 202 siempre, con el mismo texto: exista o no una cuenta pendiente, y se envie o no. */
    @PostMapping("/reenvio")
    public ResponseEntity<MensajeNeutro> reenviar(@Valid @RequestBody CorreoRequest datos) {
        servicio.reenviar(datos.email());
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(new MensajeNeutro(VerificacionDeCorreoService.MENSAJE_REENVIO));
    }

    @ExceptionHandler(CodigoInvalidoException.class)
    public ResponseEntity<ProblemDetail> codigoInvalido(HttpServletRequest peticion) {
        return Problemas.codigoInvalido(peticion.getRequestURI());
    }

    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<ProblemDetail> demasiadosIntentos(HttpServletRequest peticion) {
        return Problemas.demasiadosIntentos(peticion.getRequestURI());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> malFormada(HttpServletRequest peticion) {
        return Problemas.datosInvalidos(peticion.getRequestURI());
    }
}
