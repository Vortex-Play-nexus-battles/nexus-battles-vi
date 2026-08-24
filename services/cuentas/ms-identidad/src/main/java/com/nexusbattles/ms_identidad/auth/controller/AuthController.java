package com.nexusbattles.ms_identidad.auth.controller;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.service.RegistroService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private RegistroService registroService;

    @PostMapping("/registro")
    public ResponseEntity<?> registrarUsuario(@Valid @RequestBody Usuario nuevoUsuario) {
        try {
            Usuario usuarioRegistrado = registroService.registrarUsuario(nuevoUsuario);
            return ResponseEntity.status(HttpStatus.CREATED).body(usuarioRegistrado);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}