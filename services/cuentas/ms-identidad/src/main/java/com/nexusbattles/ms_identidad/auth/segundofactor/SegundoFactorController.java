package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.codigos.IpDelCliente;
import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.ActivacionResponse;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.CodigoDeSegundoFactorRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.DesactivarSegundoFactorRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.EnrolamientoResponse;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.EstadoSegundoFactorResponse;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code /api/v1/auth/segundo-factor} — el segundo factor de la PROPIA cuenta
 * (HU-AUT-007, ms-identidad-auth.yaml 2.2.0). Exige sesion: quien consulta,
 * enrola, activa o desactiva es quien firma el token ({@code uid}, o el apodo
 * en tokens anteriores al uid), nunca un identificador del cuerpo o de la ruta.
 *
 * <p>Los rechazos de una operacion con sesion son 409, 422, 423 o 503, nunca
 * 401/403: la interfaz toma un 401/403 por sesion caducada y llevaria al login
 * a quien solo se equivoco de codigo.
 */
@RestController
@RequestMapping("/api/v1/auth/segundo-factor")
public class SegundoFactorController {

    private final SegundoFactorService servicio;
    private final UsuarioRepository usuarios;

    public SegundoFactorController(SegundoFactorService servicio, UsuarioRepository usuarios) {
        this.servicio = servicio;
        this.usuarios = usuarios;
    }

    @GetMapping
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<EstadoSegundoFactorResponse> estado(HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.estado(quienFirma(peticion)));
    }

    @PostMapping("/enrolamiento")
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<EnrolamientoResponse> iniciarEnrolamiento(HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.iniciarEnrolamiento(quienFirma(peticion)));
    }

    @PostMapping("/activacion")
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<ActivacionResponse> activar(@Valid @RequestBody CodigoDeSegundoFactorRequest datos,
                                                      HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ActivacionResponse(true,
                        servicio.activar(quienFirma(peticion), datos.codigo(), IpDelCliente.de(peticion))));
    }

    @PostMapping("/desactivacion")
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<Void> desactivar(@Valid @RequestBody DesactivarSegundoFactorRequest datos,
                                           HttpServletRequest peticion) {
        servicio.desactivar(quienFirma(peticion), datos, IpDelCliente.de(peticion));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(SegundoFactorRechazadoException.class)
    public ResponseEntity<ProblemDetail> rechazada(SegundoFactorRechazadoException rechazo,
                                                   HttpServletRequest peticion) {
        return Problemas.de(HttpStatus.valueOf(rechazo.getMotivo().estado()), rechazo.getMotivo().tipo(),
                rechazo.getMotivo().titulo(), rechazo.getMessage(), peticion.getRequestURI());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> malFormada(HttpServletRequest peticion) {
        return Problemas.datosInvalidos(peticion.getRequestURI());
    }

    /**
     * La cuenta del token ya validado por {@code SecurityInterceptor}. Si no
     * aparece (se borro despues de emitir el token), 403: el interceptor es
     * fail-closed y esto tambien.
     */
    private Usuario quienFirma(HttpServletRequest peticion) {
        Object uid = peticion.getAttribute("uidActual");
        Optional<Usuario> usuario = Optional.empty();
        if (uid != null) {
            try {
                usuario = usuarios.findByPublicId(UUID.fromString(uid.toString()));
            } catch (IllegalArgumentException noEsUuid) {
                usuario = Optional.empty();
            }
        } else if (peticion.getAttribute("usuarioActual") instanceof String apodo) {
            usuario = usuarios.findByApodo(apodo);
        }
        return usuario.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No se pudo identificar la cuenta de la sesión."));
    }
}
