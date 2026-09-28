package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.dto.AdminUsuarioResumenResponse;
import com.nexusbattles.ms_identidad.admin.dto.BanearCuentaRequest;
import com.nexusbattles.ms_identidad.admin.dto.SuspenderCuentaRequest;
import com.nexusbattles.ms_identidad.admin.service.AdminGestionUsuarioService;
import com.nexusbattles.ms_identidad.perfiles.dto.ActualizarPerfilRequest;
import com.nexusbattles.ms_identidad.perfiles.dto.PerfilUsuarioResponse;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import com.nexusbattles.ms_identidad.sanciones.SancionRechazadaException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/admin/usuarios")
public class AdminGestionUsuarioController {

    private final AdminGestionUsuarioService adminGestionUsuarioService;

    public AdminGestionUsuarioController(AdminGestionUsuarioService adminGestionUsuarioService) {
        this.adminGestionUsuarioService = adminGestionUsuarioService;
    }

    @GetMapping("/{usuarioId}")
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public ResponseEntity<?> obtenerUsuario(@PathVariable Long usuarioId) {
        try {
            PerfilUsuario perfil = adminGestionUsuarioService.obtenerUsuarioParaGestion(usuarioId);
            return ResponseEntity.ok(AdminUsuarioResumenResponse.from(perfil));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping(value = "/{usuarioId}/perfil", consumes = "multipart/form-data")
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public ResponseEntity<?> editarPerfil(@PathVariable Long usuarioId,
                                          @Valid @ModelAttribute ActualizarPerfilRequest datos,
                                          HttpServletRequest request) {
        try {
            String administradorId = (String) request.getAttribute("usuarioActual");
            PerfilUsuario actualizado = adminGestionUsuarioService.editarPerfilDeUsuario(
                usuarioId, datos.getNombres(), datos.getApellidos(), datos.getAvatar(),
                datos.getPreferencias(), datos.getApodo(),
                administradorId, obtenerIpReal(request));
            return ResponseEntity.ok(PerfilUsuarioResponse.from(actualizado));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    /**
     * B2: delega en moderacion-sanciones con el token de quien actua (ver
     * {@link AdminGestionUsuarioService}). Si moderacion no responde, 503
     * {@code moderacion-no-disponible} (ModeracionNoDisponibleAdvice).
     */
    @PutMapping("/{usuarioId}/suspender")
    @RequirePermission(Action.SUSPENDER_USUARIOS)
    public ResponseEntity<?> suspender(@PathVariable Long usuarioId,
                                       @Valid @RequestBody SuspenderCuentaRequest datos,
                                       HttpServletRequest request) {
        try {
            String administradorId = (String) request.getAttribute("usuarioActual");
            adminGestionUsuarioService.suspenderCuenta(usuarioId, datos.getSuspendidoHasta(), datos.getMotivo(),
                request.getHeader(HttpHeaders.AUTHORIZATION), administradorId, obtenerIpReal(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (SancionRechazadaException e) {
            return rechazoDeModeracion(e, request);
        }
    }

    /** El cuerpo es opcional (B2): {@code {"motivo": "..."}} para dejar la causal en el historial. */
    @PutMapping("/{usuarioId}/banear")
    @RequirePermission(Action.BANEAR_DEFINITIVAMENTE)
    public ResponseEntity<?> banear(@PathVariable Long usuarioId,
                                    @RequestBody(required = false) BanearCuentaRequest datos,
                                    HttpServletRequest request) {
        try {
            String administradorId = (String) request.getAttribute("usuarioActual");
            adminGestionUsuarioService.banearCuenta(usuarioId, datos == null ? null : datos.motivo(),
                request.getHeader(HttpHeaders.AUTHORIZATION), administradorId, obtenerIpReal(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (SancionRechazadaException e) {
            return rechazoDeModeracion(e, request);
        }
    }

    @PutMapping("/{usuarioId}/reactivar")
    @RequirePermission(Action.SUSPENDER_USUARIOS)
    public ResponseEntity<?> reactivar(@PathVariable Long usuarioId, HttpServletRequest request) {
        try {
            String administradorId = (String) request.getAttribute("usuarioActual");
            adminGestionUsuarioService.reactivarCuenta(usuarioId, request.getHeader(HttpHeaders.AUTHORIZATION),
                administradorId, obtenerIpReal(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (SancionRechazadaException e) {
            return rechazoDeModeracion(e, request);
        }
    }

    @PostMapping("/{usuarioId}/restablecer-password")
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public ResponseEntity<?> restablecerPassword(@PathVariable Long usuarioId, HttpServletRequest request) {
        try {
            String administradorId = (String) request.getAttribute("usuarioActual");
            adminGestionUsuarioService.restablecerPassword(usuarioId, administradorId, obtenerIpReal(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    /**
     * Lo que moderacion-sanciones contesto, con el mismo sentido: 403 cuando
     * el rol no alcanza (problem details, como el resto de 403 de este
     * servicio), y el texto plano heredado de estas rutas para 400, 404 y 409.
     */
    static ResponseEntity<?> rechazoDeModeracion(SancionRechazadaException rechazo, HttpServletRequest request) {
        int estado = rechazo.getEstado();
        if (estado == 401 || estado == 403) {
            ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, rechazo.getMessage());
            problema.setType(URI.create("https://nexusbattles.upb.edu.co/errors/forbidden"));
            problema.setTitle("Acceso denegado");
            problema.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
        }
        HttpStatus respuesta = switch (estado) {
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(respuesta).body(rechazo.getMessage());
    }

    private String obtenerIpReal(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
