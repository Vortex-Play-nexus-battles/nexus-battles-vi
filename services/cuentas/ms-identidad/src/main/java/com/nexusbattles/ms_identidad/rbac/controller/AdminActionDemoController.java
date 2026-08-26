package com.nexusbattles.ms_identidad.rbac.controller;

import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
