package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.dto.AdminUsuarioResumenResponse;
import com.nexusbattles.ms_identidad.admin.dto.SuspenderCuentaRequest;
import com.nexusbattles.ms_identidad.admin.service.AdminGestionUsuarioService;
import com.nexusbattles.ms_identidad.perfiles.dto.ActualizarPerfilRequest;
import com.nexusbattles.ms_identidad.perfiles.dto.PerfilUsuarioResponse;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/usuarios")
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

    @PutMapping("/{usuarioId}/perfil")
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public ResponseEntity<?> editarPerfil(@PathVariable Long usuarioId,
                                          @Valid @RequestBody ActualizarPerfilRequest datos,
                                          HttpServletRequest request) {
        try {
            String administradorId = (String) request.getAttribute("usuarioActual");
            PerfilUsuario actualizado = adminGestionUsuarioService.editarPerfilDeUsuario(
                usuarioId, datos.getNombres(), datos.getApellidos(), datos.getAvatar(),
                datos.getBiografia(), datos.getPreferencias(), datos.getApodo(),
                administradorId, obtenerIpReal(request));
            return ResponseEntity.ok(PerfilUsuarioResponse.from(actualizado));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/{usuarioId}/suspender")
    @RequirePermission(Action.SUSPENDER_USUARIOS)
    public ResponseEntity<?> suspender(@PathVariable Long usuarioId,
                                       @Valid @RequestBody SuspenderCuentaRequest datos,
                                       HttpServletRequest request) {
        try {
            String administradorId = (String) request.getAttribute("usuarioActual");
            adminGestionUsuarioService.suspenderCuenta(usuarioId, datos.getSuspendidoHasta(),
                administradorId, obtenerIpReal(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/{usuarioId}/banear")
    @RequirePermission(Action.BANEAR_DEFINITIVAMENTE)
    public ResponseEntity<?> banear(@PathVariable Long usuarioId, HttpServletRequest request) {
        try {
            String administradorId = (String) request.getAttribute("usuarioActual");
            adminGestionUsuarioService.banearCuenta(usuarioId, administradorId, obtenerIpReal(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/{usuarioId}/reactivar")
    @RequirePermission(Action.SUSPENDER_USUARIOS)
    public ResponseEntity<?> reactivar(@PathVariable Long usuarioId, HttpServletRequest request) {
        try {
            String administradorId = (String) request.getAttribute("usuarioActual");
            adminGestionUsuarioService.reactivarCuenta(usuarioId, administradorId, obtenerIpReal(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
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

    private String obtenerIpReal(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
