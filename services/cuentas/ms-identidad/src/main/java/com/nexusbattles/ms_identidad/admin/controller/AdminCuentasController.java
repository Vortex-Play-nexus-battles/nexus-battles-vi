package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.dto.CrearCuentaAdminRequest;
import com.nexusbattles.ms_identidad.admin.service.AdminCuentaService;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/cuentas")
public class AdminCuentasController {

    private final AdminCuentaService adminCuentaService;

    public AdminCuentasController(AdminCuentaService adminCuentaService) {
        this.adminCuentaService = adminCuentaService;
    }

    @PostMapping
    @RequirePermission(Action.CREAR_ADMIN_MODERADOR)
    public ResponseEntity<?> crearCuentaAdministrativa(@Valid @RequestBody CrearCuentaAdminRequest datos,
                                                       HttpServletRequest request) {
        try {
            String administradorId = request.getHeader("X-User-Name");
            Usuario usuarioCreado = adminCuentaService.crearCuentaAdministrativa(datos, administradorId);
            return ResponseEntity.status(HttpStatus.CREATED).body(usuarioCreado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
