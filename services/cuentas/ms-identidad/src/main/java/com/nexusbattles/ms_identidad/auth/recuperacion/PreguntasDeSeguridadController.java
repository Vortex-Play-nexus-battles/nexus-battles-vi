package com.nexusbattles.ms_identidad.auth.recuperacion;

import com.nexusbattles.ms_identidad.auth.codigos.IpDelCliente;
import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code /api/v1/auth/preguntas-seguridad} — las preguntas de la PROPIA cuenta
 * (B1, 7.1.1). Exige sesion: quien consulta o cambia es quien firma el token
 * ({@code uid}, o el apodo en tokens anteriores al uid), nunca un
 * identificador del cuerpo o de la ruta.
 */
@RestController
@RequestMapping("/api/v1/auth/preguntas-seguridad")
public class PreguntasDeSeguridadController {

    private final PreguntasDeSeguridadService servicio;
    private final UsuarioRepository usuarios;

    public PreguntasDeSeguridadController(PreguntasDeSeguridadService servicio, UsuarioRepository usuarios) {
        this.servicio = servicio;
        this.usuarios = usuarios;
    }

    @GetMapping
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<PreguntasDeRecuperacion> consultar(HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.deLaCuenta(quienFirma(peticion).getId()));
    }

    @PutMapping
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<PreguntasDeRecuperacion> configurar(@Valid @RequestBody ConfigurarPreguntasRequest datos,
                                                              HttpServletRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.configurar(quienFirma(peticion).getId(), datos, IpDelCliente.de(peticion)));
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
