package com.nexusbattles.ms_identidad.admin.controller;

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

    @PutMapping("/{usuarioId}/perfil")
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public ResponseEntity<?> editarPerfil(@PathVariable Long usuarioId,
                                          @Valid @RequestBody ActualizarPerfilRequest datos,
                                          HttpServletRequest request) {
        try {
            PerfilUsuario actualizado = adminGestionUsuarioService.editarPerfilDeUsuario(
                usuarioId, datos.getNombres(), datos.getApellidos(), datos.getAvatar(),
                datos.getBiografia(), datos.getPreferencias(), datos.getApodo(),
                request.getHeader("X-User-Name"));
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
            adminGestionUsuarioService.suspenderCuenta(usuarioId, datos.getSuspendidoHasta(),
                request.getHeader("X-User-Name"));
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
            adminGestionUsuarioService.banearCuenta(usuarioId, request.getHeader("X-User-Name"));
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
            adminGestionUsuarioService.reactivarCuenta(usuarioId, request.getHeader("X-User-Name"));
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
            adminGestionUsuarioService.restablecerPassword(usuarioId, request.getHeader("X-User-Name"));
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}
