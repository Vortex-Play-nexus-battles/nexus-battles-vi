package com.nexusbattles.ms_identidad.perfiles.controller;

import com.nexusbattles.ms_identidad.perfiles.dto.ActualizarPerfilRequest;
import com.nexusbattles.ms_identidad.perfiles.dto.PerfilUsuarioResponse;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
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
