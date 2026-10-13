package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBaneadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaInactivaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaNoVerificadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaSuspendidaException;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.AccesoConSegundoFactorResponse;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.ActivacionConDesafioRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.CanjeDeDesafioRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.DesafioRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.EnrolamientoResponse;
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
 * {@code /api/v1/auth/login/segundo-factor} — el segundo paso del login
 * (HU-AUT-007, ms-identidad-auth.yaml 2.2.0).
 *
 * <p><b>Rutas publicas</b>, como el propio login: quien llega aqui todavia no
 * tiene sesion, y lo que prueba que ya dio su contrasena es el desafio. Sin
 * {@code @RequirePermission} a proposito. El borde las limita igual que el
 * login ({@code zone=acceso}: la expresion {@code POST /api/v1/auth/login...}
 * las cubre), y cada codigo incorrecto cuenta como intento fallido de la
 * cuenta (RF-AUT-009).
 *
 * <p>Siempre problem details (rutas nuevas, sin clientes de texto plano que
 * conservar). La IP y el agente entran igual que en el login, para el aviso de
 * dispositivo nuevo.
 */
@RestController
@RequestMapping("/api/v1/auth/login/segundo-factor")
public class SegundoPasoController {

    private final SegundoPasoDelLogin segundoPaso;

    public SegundoPasoController(SegundoPasoDelLogin segundoPaso) {
        this.segundoPaso = segundoPaso;
    }

    @PostMapping
    public ResponseEntity<AccesoConSegundoFactorResponse> canjear(@Valid @RequestBody CanjeDeDesafioRequest datos,
                                                                  HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(segundoPaso.canjear(datos, ipDe(peticion), peticion.getHeader("User-Agent")));
    }

    @PostMapping("/enrolamiento")
    public ResponseEntity<EnrolamientoResponse> enrolar(@Valid @RequestBody DesafioRequest datos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(segundoPaso.enrolarConDesafio(datos));
    }

    @PostMapping("/activacion")
    public ResponseEntity<AccesoConSegundoFactorResponse> activar(
            @Valid @RequestBody ActivacionConDesafioRequest datos, HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(segundoPaso.activarConDesafio(datos, ipDe(peticion), peticion.getHeader("User-Agent")));
    }

    @ExceptionHandler(SegundoFactorRechazadoException.class)
    public ResponseEntity<ProblemDetail> rechazada(SegundoFactorRechazadoException rechazo,
                                                   HttpServletRequest peticion) {
        return Problemas.de(HttpStatus.valueOf(rechazo.getMotivo().estado()), rechazo.getMotivo().tipo(),
                rechazo.getMotivo().titulo(), rechazo.getMessage(), peticion.getRequestURI());
    }

    /** Los mismos 403 que el login: una sancion pudo llegar entre los dos pasos. */
    @ExceptionHandler({CuentaBaneadaException.class, CuentaSuspendidaException.class,
        CuentaInactivaException.class, CuentaNoVerificadaException.class})
    public ResponseEntity<ProblemDetail> cuentaQueNoPuedeEntrar(RuntimeException rechazo,
                                                                HttpServletRequest peticion) {
        String tipo;
        String titulo;
        if (rechazo instanceof CuentaBaneadaException) {
            tipo = "cuenta-baneada";
            titulo = "Cuenta baneada";
        } else if (rechazo instanceof CuentaSuspendidaException) {
            tipo = "cuenta-suspendida";
            titulo = "Cuenta suspendida";
        } else if (rechazo instanceof CuentaInactivaException) {
            tipo = "cuenta-inactiva";
            titulo = "Cuenta inactiva";
        } else {
            tipo = "cuenta-no-verificada";
            titulo = "Correo sin verificar";
        }
        ResponseEntity<ProblemDetail> respuesta = Problemas.de(HttpStatus.FORBIDDEN, tipo, titulo,
                rechazo.getMessage(), peticion.getRequestURI());
        if (rechazo instanceof CuentaSuspendidaException suspendida && suspendida.getHasta() != null
                && respuesta.getBody() != null) {
            respuesta.getBody().setProperty("suspendidoHasta", suspendida.getHasta().toInstant().toString());
        }
        return respuesta;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> malFormada(HttpServletRequest peticion) {
        return Problemas.datosInvalidos(peticion.getRequestURI());
    }

    private static String ipDe(HttpServletRequest peticion) {
        // Igual que el login (AuthController): la cabecera tal cual, que es con
        // lo que se calculo la huella del dispositivo en el primer paso.
        String ip = peticion.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = peticion.getRemoteAddr();
        }
        return ip;
    }
}
