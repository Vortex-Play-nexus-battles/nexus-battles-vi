package com.nexusbattles.ms_identidad.rbac.controller;

import com.nexusbattles.ms_identidad.rbac.dto.AuthorizationRequest;
import com.nexusbattles.ms_identidad.rbac.dto.AuthorizationResponse;
import com.nexusbattles.ms_identidad.rbac.dto.PermissionMatrixResponse;
import com.nexusbattles.ms_identidad.rbac.dto.RoleDescriptor;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.model.PermissionType;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * La politica RBAC, consultable solo por quien administra cuentas.
 *
 * <h2>R9.5 — por que estos tres metodos llevan {@code @RequirePermission}</h2>
 *
 * Hasta R9.5 este controlador <b>no tenia ninguna anotacion</b>, y el
 * {@code SecurityInterceptor} solo actua cuando la encuentra. Es decir: los
 * tres caminos estaban completamente abiertos. Cualquiera podia pedir
 * {@code GET /matrix} y llevarse la tabla entera de quien puede hacer que
 * —48 celdas, los cuatro roles, las doce acciones— o usar
 * {@code POST /authorize} como oraculo para sondear la politica accion por
 * accion sin acreditar nada. No es una fuga de datos personales, pero si el
 * mapa de la superficie administrativa servido a quien lo pida.
 *
 * <p>La accion elegida es {@link Action#GESTIONAR_CUENTAS} porque es
 * exactamente la que la interfaz ya exigia: {@code gestion-usuarios.js} es el
 * unico consumidor de {@code /matrix}, envia su Bearer, y esconde la pantalla
 * entera con {@code checkPermission(rol, 'GESTIONAR_CUENTAS')}. Un MODERADOR
 * ya veia "acceso denegado" ahi. Lo que cambia con R9.5 no es quien entra,
 * sino <b>donde se decide</b>: el servidor deja de creerle a la pantalla.
 * Es la HU-RBAC-004 aplicada a su propio controlador — "la interfaz nunca es
 * perimetro de confianza" valia para todos menos para el.
 *
 * <p>{@code @CrossOrigin(origins = "*")} tambien se fue de aqui: la politica
 * de origenes la fija {@code WebSecurityConfig} para todo el servicio, por
 * variable de entorno, y una anotacion suelta con comodin la contradecia.
 */
@RestController
@RequestMapping("/api/v1/rbac")
public class RbacController {

    private final RbacAuthorizationService rbacService;

    public RbacController(RbacAuthorizationService rbacService) {
        this.rbacService = rbacService;
    }

    @PostMapping("/authorize")
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public ResponseEntity<AuthorizationResponse> evaluatePermission(@Valid @RequestBody AuthorizationRequest request) {
        PermissionType type = rbacService.evaluatePermission(request.getRole(), request.getAction());
        boolean permitted = (type == PermissionType.GRANTED || type == PermissionType.TEMPORARY);
        String reason = permitted ? "Acción autorizada por política RBAC" : "Acceso denegado: el rol no cuenta con permisos suficientes para esta acción";

        return ResponseEntity.ok(new AuthorizationResponse(permitted, type, request.getRole(), request.getAction(), reason));
    }

    @GetMapping("/roles")
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public ResponseEntity<List<RoleDescriptor>> getRoles() {
        List<RoleDescriptor> roles = Arrays.stream(Role.values())
                .map(r -> new RoleDescriptor(r, r.getDisplayName(), r.getDescription()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/matrix")
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public ResponseEntity<PermissionMatrixResponse> getMatrix() {
        return ResponseEntity.ok(new PermissionMatrixResponse("1.1.0 (Tabla 24 extendida)", rbacService.getFullMatrix()));
    }
}
