package com.nexusbattles.ms_identidad.rbac.controller;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.rbac.dto.ChangeRoleRequest;
import com.nexusbattles.ms_identidad.rbac.service.RoleAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/rbac/users")
@CrossOrigin(origins = "*")
public class RoleAssignmentController {

    private final RoleAssignmentService roleAssignmentService;

    public RoleAssignmentController(RoleAssignmentService roleAssignmentService) {
        this.roleAssignmentService = roleAssignmentService;
    }

    @PutMapping("/{idUsuario}/role")
    public ResponseEntity<?> cambiarRol(
            @PathVariable Long idUsuario,
            @Valid @RequestBody ChangeRoleRequest request) {

        try {
            Usuario usuarioActualizado = roleAssignmentService.cambiarRol(
                    request.getIdSolicitante(),
                    idUsuario,
                    request.getNuevoRol()
            );

            return ResponseEntity.ok(
                    Map.of(
                            "mensaje", "Rol actualizado correctamente",
                            "usuarioId", usuarioActualizado.getId(),
                            "nuevoRol", usuarioActualizado.getRol()
                    )
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error", e.getMessage()
                    )
            );
        }
    }
}