package com.nexusbattles.ms_identidad.perfiles.controller;

import com.nexusbattles.ms_identidad.auth.validation.ApodoNoPermitidoException;
import com.nexusbattles.ms_identidad.perfiles.dto.ActualizarPerfilRequest;
import com.nexusbattles.ms_identidad.perfiles.dto.PerfilUsuarioResponse;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.service.ApodoEnUsoException;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * HU-USR-001: el jugador consulta y edita su propio perfil.
 *
 * <p><b>Qué identificador va en la ruta.</b> Hasta R16 era {@code Long usuarioId},
 * el id autonumérico de la tabla. El navegador nunca lo conoce: lo único que
 * tiene es el {@code uid} del token, el UUID público de ADR-002, y es lo que
 * {@code cuenta.js} manda. Spring no podía convertir un UUID a {@code Long}, la
 * petición moría con un 4xx antes de llegar al controlador y la pantalla «Mi
 * cuenta» mostraba «No pudimos cargar tu perfil» a <em>todos</em> los
 * jugadores. Ahora la ruta acepta el uid; el id numérico se sigue aceptando
 * para no romper a nadie que lo use, aunque ningún consumidor del repositorio
 * lo hace.
 *
 * <p><b>Quién es el dueño.</b> Se compara el uid del token con el del perfil.
 * Comparar solo el apodo —lo que se hacía— falla en cuanto el jugador cambia
 * de apodo desde este mismo formulario: su token sigue diciendo el apodo
 * viejo hasta que vuelva a entrar, y la siguiente lectura daba 403. El apodo
 * queda como respaldo para tokens emitidos antes de que existiera el uid.
 */
@RestController
@RequestMapping("/api/v1/perfiles")
public class PerfilController {

    private static final Pattern SOLO_DIGITOS = Pattern.compile("\\d{1,18}");

    private final PerfilUsuarioService perfilUsuarioService;

    public PerfilController(PerfilUsuarioService perfilUsuarioService) {
        this.perfilUsuarioService = perfilUsuarioService;
    }

    @GetMapping("/{usuario}")
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<PerfilUsuarioResponse> obtenerMiPerfil(@PathVariable String usuario,
                                                                 HttpServletRequest request) {
        PerfilUsuario perfil = buscarOFallar(usuario);
        verificarDueno(perfil, request);
        return ResponseEntity.ok(PerfilUsuarioResponse.from(perfil));
    }

    // Cambió de @RequestBody (JSON) a @ModelAttribute + multipart/form-data,
    // porque ahora el avatar es un archivo real (mismo patrón que usa
    // Cristian en /api/v1/auth/registro), no un nombre de avatar predefinido.
    @PutMapping(value = "/{usuario}", consumes = "multipart/form-data")
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<?> actualizarMiPerfil(@PathVariable String usuario,
                                                @Valid @ModelAttribute ActualizarPerfilRequest datos,
                                                HttpServletRequest request) {
        PerfilUsuario perfilActual = buscarOFallar(usuario);
        verificarDueno(perfilActual, request);
        try {
            PerfilUsuario actualizado = perfilUsuarioService.actualizarPerfilPropio(
                perfilActual.getUsuario().getId(), datos.getNombres(), datos.getApellidos(),
                datos.getAvatar(), datos.getPreferencias(), datos.getApodo());
            return ResponseEntity.ok(PerfilUsuarioResponse.from(actualizado));
        } catch (IllegalArgumentException e) {
            return rechazo(e, request);
        }
    }

    /** Raiz de los {@code type} de error de identidad (la misma del registro). */
    static final String TIPOS = "https://nexusbattles.upb.edu.co/errors/";

    /**
     * El 400 del cambio de perfil. Quien pide {@code application/problem+json}
     * recibe un problem details con un {@code type} que dice por que
     * (ms-identidad-perfiles.yaml 1.3.0, RFINAL-03); quien no, el texto plano de
     * siempre.
     *
     * <p>Antes solo existia el texto, y «Mi cuenta» lee JSON: el motivo se
     * perdia y la vista decia «Revisa los datos e inténtalo otra vez» ante un
     * apodo prohibido (informe del jugador del 4-oct, «batman»). El {@code type}
     * no revela que termino de la lista negra salto ni su categoria: el detalle
     * es la frase generica de moderacion.
     */
    static ResponseEntity<?> rechazo(IllegalArgumentException e, HttpServletRequest request) {
        if (!pideProblemDetails(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
        String motivo;
        String titulo;
        if (e instanceof ApodoNoPermitidoException) {
            motivo = "apodo-no-permitido";
            titulo = "El apodo no está permitido";
        } else if (e instanceof ApodoEnUsoException) {
            motivo = "apodo-en-uso";
            titulo = "El apodo ya está en uso";
        } else {
            motivo = "perfil-invalido";
            titulo = "Los datos del perfil no son válidos";
        }
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problema.setType(URI.create(TIPOS + motivo));
        problema.setTitle(titulo);
        problema.setInstance(URI.create(request.getRequestURI()));
        if (!"perfil-invalido".equals(motivo)) {
            problema.setProperty("campo", "apodo");
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problema);
    }

    private static boolean pideProblemDetails(HttpServletRequest request) {
        String acepta = request.getHeader("Accept");
        return acepta != null && acepta.toLowerCase(java.util.Locale.ROOT).contains("application/problem+json");
    }

    /**
     * Resuelve el segmento de la ruta. Un valor que no es ni UUID ni número no
     * puede nombrar a nadie: se responde 404, igual que a un perfil inexistente,
     * para no distinguir entre «mal escrito» y «no existe».
     */
    private PerfilUsuario buscarOFallar(String usuario) {
        try {
            UUID identificadorPublico = comoUuid(usuario);
            if (identificadorPublico != null) {
                return perfilUsuarioService.obtenerPorIdentificadorPublico(identificadorPublico);
            }
            if (usuario != null && SOLO_DIGITOS.matcher(usuario).matches()) {
                return perfilUsuarioService.obtenerPorUsuarioId(Long.parseLong(usuario));
            }
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe ese perfil.");
    }

    private static UUID comoUuid(String valor) {
        if (valor == null || valor.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void verificarDueno(PerfilUsuario perfil, HttpServletRequest request) {
        String uidSolicitante = (String) request.getAttribute("uidActual");
        UUID uidDueno = perfil.getUsuario().getPublicId();
        if (uidSolicitante != null && uidDueno != null) {
            if (!uidSolicitante.equalsIgnoreCase(uidDueno.toString())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No puedes acceder al perfil de otro usuario.");
            }
            return;
        }
        String solicitante = (String) request.getAttribute("usuarioActual");
        String dueno = perfil.getUsuario().getApodo();
        if (solicitante == null || !solicitante.equalsIgnoreCase(dueno)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes acceder al perfil de otro usuario.");
        }
    }
}
