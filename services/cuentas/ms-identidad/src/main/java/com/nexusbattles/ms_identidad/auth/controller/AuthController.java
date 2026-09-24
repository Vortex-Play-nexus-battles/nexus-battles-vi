package com.nexusbattles.ms_identidad.auth.controller;

import com.nexusbattles.ms_identidad.auth.dto.CanjearTokenRequest;
import com.nexusbattles.ms_identidad.auth.dto.LoginRequest;
import com.nexusbattles.ms_identidad.auth.dto.LoginResponse;
import com.nexusbattles.ms_identidad.auth.dto.RegistroRequest;
import com.nexusbattles.ms_identidad.auth.dto.SolicitarRestablecimientoRequest;
import com.nexusbattles.ms_identidad.auth.exception.CredencialesInvalidasException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBaneadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBloqueadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaInactivaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaSuspendidaException;
import com.nexusbattles.ms_identidad.auth.exception.RegistroRechazadoException;
import com.nexusbattles.ms_identidad.auth.exception.RegistroRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.auth.exception.TokenInvalidoException;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.LoginService;
import com.nexusbattles.ms_identidad.auth.service.RegistroService;
import com.nexusbattles.ms_identidad.auth.service.TokenCredencialService;
import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String TIPOS = "https://nexusbattles.upb.edu.co/errors/";

    private final RegistroService registroService;
    private final LoginService loginService;
    private final TokenCredencialService tokenCredencialService;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaDeCuenta auditoriaDeCuenta;

    // R17 — inyeccion por constructor (Sonar S6813): antes eran campos
    // @Autowired, y cada dependencia nueva sumaba un aviso.
    public AuthController(RegistroService registroService,
                          LoginService loginService,
                          TokenCredencialService tokenCredencialService,
                          UsuarioRepository usuarioRepository,
                          AuditoriaDeCuenta auditoriaDeCuenta) {
        this.registroService = registroService;
        this.loginService = loginService;
        this.tokenCredencialService = tokenCredencialService;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaDeCuenta = auditoriaDeCuenta;
    }

    // Cambió de @RequestBody (JSON puro) a @ModelAttribute, porque ahora el
    // registro incluye un archivo (la foto de avatar), no solo texto.
    //
    // R17 — los rechazos siguen siendo 400 con el mismo texto de siempre
    // (contrato 1.0.0). Quien manda `Accept: application/problem+json` recibe
    // problem details con `type` y `campo` (regla 4), sin romper a quien no.
    // Antes, cualquier RuntimeException salia como 400 con su mensaje tal
    // cual: un error de base de datos le ensenaba el SQL a quien se
    // registraba. Ahora solo los rechazos de negocio son 400; lo inesperado
    // es un 500 sin detalles internos.
    @PostMapping(value = "/registro", consumes = "multipart/form-data")
    public ResponseEntity<?> registrarUsuario(@Valid @ModelAttribute RegistroRequest datos,
                                              HttpServletRequest request) {
        try {
            Usuario usuarioRegistrado = registroService.registrarUsuario(
                datos, request.getHeader("traceparent"), obtenerIpCliente(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(usuarioRegistrado);
        } catch (RegistroRechazadoException rechazo) {
            return rechazo(request, HttpStatus.BAD_REQUEST, rechazo.getMotivo().tipo(),
                rechazo.getMotivo().titulo(), rechazo.getMessage(), rechazo.getCampo());
        } catch (DataIntegrityViolationException carrera) {
            // Dos altas con el mismo correo o apodo a la vez: las dos pasan la
            // comprobacion y la segunda choca con la restriccion UNIQUE. Se
            // responde lo mismo que si hubiera llegado despues.
            return rechazo(request, HttpStatus.BAD_REQUEST, duplicadoDe(datos));
        } catch (IllegalArgumentException invalido) {
            return rechazo(request, HttpStatus.BAD_REQUEST, Motivo.DATOS_INVALIDOS.tipo(),
                Motivo.DATOS_INVALIDOS.titulo(), invalido.getMessage(), null);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> iniciarSesion(@Valid @RequestBody LoginRequest datos,
                                           HttpServletRequest request) {
        try {
            String ip = obtenerIpCliente(request);
            String userAgent = request.getHeader("User-Agent");

            LoginResponse respuesta = loginService.iniciarSesion(datos, ip, userAgent);
            return ResponseEntity.ok(respuesta);
        } catch (CredencialesInvalidasException e) {
            return rechazo(request, HttpStatus.UNAUTHORIZED, URI.create(TIPOS + "credenciales-invalidas"),
                "Credenciales inválidas", e.getMessage(), null);
        } catch (CuentaBaneadaException e) {
            return rechazo(request, HttpStatus.FORBIDDEN, URI.create(TIPOS + "cuenta-baneada"),
                "Cuenta baneada", e.getMessage(), null);
        } catch (CuentaSuspendidaException e) {
            return rechazo(request, HttpStatus.FORBIDDEN, URI.create(TIPOS + "cuenta-suspendida"),
                "Cuenta suspendida", e.getMessage(), null);
        } catch (CuentaInactivaException e) {
            return rechazo(request, HttpStatus.FORBIDDEN, URI.create(TIPOS + "cuenta-inactiva"),
                "Cuenta inactiva", e.getMessage(), null);
        } catch (CuentaBloqueadaException e) {
            return rechazo(request, HttpStatus.LOCKED, URI.create(TIPOS + "cuenta-bloqueada"),
                "Cuenta bloqueada temporalmente", e.getMessage(), null);
        }
    }

    /**
     * R17 — cierre de sesion.
     *
     * <p>Los tokens son JWT sin estado: cerrar sesion es que el navegador
     * olvide el suyo, y eso lo hace la interfaz aunque esta llamada falle.
     * Lo que aporta el servidor es la auditoria del cierre. El token sigue
     * siendo valido hasta que caduca; invalidarlo en todos los servicios
     * exigiria tokens de corta vida con renovacion (decision anotada, D-31).
     */
    @PostMapping("/logout")
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<Void> cerrarSesion(HttpServletRequest request) {
        Object uid = request.getAttribute("uidActual");
        Object apodo = request.getAttribute("usuarioActual");
        String quien = uid != null ? uid.toString() : String.valueOf(apodo);
        auditoriaDeCuenta.cierreDeSesion(quien, obtenerIpCliente(request));
        return ResponseEntity.noContent().build();
    }

    // HU-COR-003. Punto de entrada publico de auto-servicio ("olvide mi
    // contraseña") que faltaba por completo -- solo existia la via
    // administrativa (AdminGestionUsuarioController). Responde siempre el
    // mismo mensaje generico, exista o no la cuenta con ese email: evita
    // que este endpoint sirva para enumerar correos registrados (mismo
    // principio que el mensaje generico del login).
    @PostMapping("/restablecer/solicitar")
    public ResponseEntity<?> solicitarRestablecimiento(@Valid @RequestBody SolicitarRestablecimientoRequest datos) {
        tokenCredencialService.solicitarRestablecimiento(datos.getEmail());
        return ResponseEntity.ok("Si el correo está registrado, recibirás un mensaje con instrucciones para restablecer tu contraseña.");
    }

    @PostMapping("/restablecer/confirmar")
    public ResponseEntity<?> canjearToken(@Valid @RequestBody CanjearTokenRequest datos) {
        try {
            tokenCredencialService.canjearToken(datos.getToken(), datos.getNuevaPassword());
            return ResponseEntity.ok("Contraseña actualizada correctamente. Ya puedes iniciar sesión.");
        } catch (TokenInvalidoException | IllegalArgumentException e) {
            // IllegalArgumentException: la nueva contraseña no cumple la
            // politica (RF-AUT-002); el mensaje dice que regla falla.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    private RegistroRechazadoException duplicadoDe(RegistroRequest datos) {
        String email = datos.getEmail() == null ? "" : datos.getEmail().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            return new RegistroRechazadoException(Motivo.CORREO_EN_USO, "El correo electrónico ya está registrado.");
        }
        return new RegistroRechazadoException(Motivo.APODO_EN_USO, "El apodo ya está en uso.");
    }

    private ResponseEntity<Object> rechazo(HttpServletRequest request, HttpStatus estado,
                                           RegistroRechazadoException rechazo) {
        return rechazo(request, estado, rechazo.getMotivo().tipo(), rechazo.getMotivo().titulo(),
            rechazo.getMessage(), rechazo.getCampo());
    }

    /**
     * El rechazo en el formato que pida el cliente: problem details si lo
     * declara en {@code Accept}, el texto plano de siempre si no.
     */
    static ResponseEntity<Object> rechazo(HttpServletRequest request, HttpStatus estado, URI tipo,
                                          String titulo, String detalle, String campo) {
        if (!pideProblemDetails(request)) {
            return ResponseEntity.status(estado).body(detalle);
        }
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(tipo);
        problema.setTitle(titulo);
        problema.setInstance(URI.create(request.getRequestURI()));
        if (campo != null) {
            problema.setProperty("campo", campo);
        }
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    static boolean pideProblemDetails(HttpServletRequest request) {
        String acepta = request.getHeader(HttpHeaders.ACCEPT);
        return acepta != null && acepta.toLowerCase(Locale.ROOT).contains("application/problem+json");
    }

    private String obtenerIpCliente(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
