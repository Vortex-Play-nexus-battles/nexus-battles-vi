package com.nexusbattles.ms_identidad.rbac.controller;

import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * SOLO PARA PRUEBAS. Una ruta protegida minima sobre la que {@code
 * SecurityBypassTest} ejercita el interceptor (JWT, version, rol, cabecera de
 * desarrollo). Hasta la auditoria Super Admin del 13-oct vivia en el codigo de
 * produccion como {@code POST /api/v1/admin/ban}: respondia «Usuario X ha sido
 * baneado definitivamente» y no baneaba a nadie. El baneo real es
 * {@code PUT /api/v1/admin/usuarios/{id}/banear}, que delega en
 * moderacion-sanciones.
 *
 * <p>R9.5 — aqui habia un {@code @CrossOrigin(origins = "*")}. Se fue por la
 * misma razon que el de {@code RbacController}: la politica de origenes la
 * fija {@code WebSecurityConfig} para todo el servicio, por variable de
 * entorno, y una anotacion suelta con comodin la contradecia en el camino mas
 * sensible del servicio (banear definitivamente).
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminActionDemoController {

    @PostMapping("/ban")
    @RequirePermission(Action.BANEAR_DEFINITIVAMENTE)
    public ResponseEntity<Map<String, String>> banUser(@RequestBody Map<String, String> payload) {
        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "message", "Usuario " + payload.get("userId") + " ha sido baneado definitivamente."
        ));
    }
}
