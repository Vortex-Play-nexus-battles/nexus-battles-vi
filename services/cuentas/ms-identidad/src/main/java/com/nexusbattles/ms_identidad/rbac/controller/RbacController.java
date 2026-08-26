package com.nexusbattles.ms_identidad.rbac.controller;

import com.nexusbattles.ms_identidad.rbac.dto.AuthorizationRequest;
import com.nexusbattles.ms_identidad.rbac.dto.AuthorizationResponse;
import com.nexusbattles.ms_identidad.rbac.dto.PermissionMatrixResponse;
import com.nexusbattles.ms_identidad.rbac.dto.RoleDescriptor;
import com.nexusbattles.ms_identidad.rbac.model.PermissionType;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rbac")
@CrossOrigin(origins = "*")
public class RbacController {

    private final RbacAuthorizationService rbacService;

    public RbacController(RbacAuthorizationService rbacService) {
        this.rbacService = rbacService;
    }

    @PostMapping("/authorize")
    public ResponseEntity<AuthorizationResponse> evaluatePermission(@Valid @RequestBody AuthorizationRequest request) {
        PermissionType type = rbacService.evaluatePermission(request.getRole(), request.getAction());
        boolean permitted = (type == PermissionType.GRANTED || type == PermissionType.TEMPORARY);
        String reason = permitted ? "Acción autorizada por política RBAC" : "Acceso denegado según la Tabla 24 (Default-Deny)";

        return ResponseEntity.ok(new AuthorizationResponse(permitted, type, request.getRole(), request.getAction(), reason));
    }

    @GetMapping("/roles")
    public ResponseEntity<List<RoleDescriptor>> getRoles() {
        List<RoleDescriptor> roles = Arrays.stream(Role.values())
                .map(r -> new RoleDescriptor(r, r.getDisplayName(), r.getDescription()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/matrix")
    public ResponseEntity<PermissionMatrixResponse> getMatrix() {
        return ResponseEntity.ok(new PermissionMatrixResponse("1.0.0 (Tabla 24)", rbacService.getFullMatrix()));
    }
}
