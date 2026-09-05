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

@RestController
@RequestMapping("/api/v1/perfiles")
public class PerfilController {

    private final PerfilUsuarioService perfilUsuarioService;

    public PerfilController(PerfilUsuarioService perfilUsuarioService) {
        this.perfilUsuarioService = perfilUsuarioService;
    }

    @GetMapping("/{usuarioId}")
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<PerfilUsuarioResponse> obtenerMiPerfil(@PathVariable Long usuarioId,
                                                                 HttpServletRequest request) {
        PerfilUsuario perfil = buscarOFallar(usuarioId);
        verificarDueno(perfil, request);
        return ResponseEntity.ok(PerfilUsuarioResponse.from(perfil));
    }

    @PutMapping("/{usuarioId}")
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<?> actualizarMiPerfil(@PathVariable Long usuarioId,
                                                @Valid @RequestBody ActualizarPerfilRequest datos,
                                                HttpServletRequest request) {
        PerfilUsuario perfilActual = buscarOFallar(usuarioId);
        verificarDueno(perfilActual, request);
        try {
            PerfilUsuario actualizado = perfilUsuarioService.actualizarPerfilPropio(
                usuarioId, datos.getNombres(), datos.getApellidos(), datos.getAvatar(),
                datos.getBiografia(), datos.getPreferencias(), datos.getApodo());
            return ResponseEntity.ok(PerfilUsuarioResponse.from(actualizado));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    private PerfilUsuario buscarOFallar(Long usuarioId) {
        try {
            return perfilUsuarioService.obtenerPorUsuarioId(usuarioId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private void verificarDueno(PerfilUsuario perfil, HttpServletRequest request) {
        String solicitante = (String) request.getAttribute("usuarioActual");
        String dueno = perfil.getUsuario().getApodo();
        if (solicitante == null || !solicitante.equalsIgnoreCase(dueno)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes acceder al perfil de otro usuario.");
        }
    }
}
