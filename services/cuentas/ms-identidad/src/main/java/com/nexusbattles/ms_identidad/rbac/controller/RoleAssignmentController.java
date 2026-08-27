package com.nexusbattles.ms_identidad.rbac.controller;

import com.nexusbattles.ms_identidad.rbac.dto.AsignarRolRequest;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import com.nexusbattles.ms_identidad.rbac.service.RoleAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/rbac/usuarios")
public class RoleAssignmentController {

    private final RoleAssignmentService roleAssignmentService;

    public RoleAssignmentController(RoleAssignmentService roleAssignmentService) {
        this.roleAssignmentService = roleAssignmentService;
    }

    @PutMapping("/{usuarioId}/rol")
    @RequirePermission(Action.ASIGNAR_ROL)
    public ResponseEntity<?> asignarRol(
            @PathVariable Long usuarioId,
            @Valid @RequestBody AsignarRolRequest datos) {

        roleAssignmentService.asignarRol(
                usuarioId,
                datos.getNuevoRol()
        );

        return ResponseEntity.ok(
                Map.of("mensaje", "Rol actualizado correctamente")
        );
    }
}