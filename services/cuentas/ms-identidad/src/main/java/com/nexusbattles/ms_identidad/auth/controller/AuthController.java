package com.nexusbattles.ms_identidad.auth.controller;

import com.nexusbattles.ms_identidad.auth.dto.CanjearTokenRequest;
import com.nexusbattles.ms_identidad.auth.dto.LoginRequest;
import com.nexusbattles.ms_identidad.auth.dto.LoginResponse;
import com.nexusbattles.ms_identidad.auth.dto.RegistroRequest;
import com.nexusbattles.ms_identidad.auth.exception.CredencialesInvalidasException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBaneadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBloqueadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaInactivaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaSuspendidaException;
import com.nexusbattles.ms_identidad.auth.exception.TokenInvalidoException;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.service.LoginService;
import com.nexusbattles.ms_identidad.auth.service.RegistroService;
import com.nexusbattles.ms_identidad.auth.service.TokenCredencialService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private RegistroService registroService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private TokenCredencialService tokenCredencialService;

    @PostMapping("/registro")
    public ResponseEntity<?> registrarUsuario(@Valid @RequestBody RegistroRequest datos) {
        try {
            Usuario usuarioRegistrado = registroService.registrarUsuario(datos);
            return ResponseEntity.status(HttpStatus.CREATED).body(usuarioRegistrado);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> iniciarSesion(@Valid @RequestBody LoginRequest datos,
                                           HttpServletRequest request) {
        try {
            String ip = obtenerIpCliente(request);
            String userAgent = request.getHeader("User-Agent");

            LoginResponse respuesta = loginService.iniciarSesion(datos, ip, userAgent);
            return ResponseEntity.ok(respuesta);

        } catch (CredencialesInvalidasException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (CuentaBaneadaException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (CuentaSuspendidaException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (CuentaInactivaException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (CuentaBloqueadaException e) {
            return ResponseEntity.status(HttpStatus.LOCKED).body(e.getMessage());
        }
    }

    // Nuevo (corrige el hallazgo de Sanabria, punto 1): el usuario llega
    // aquí desde el link de su correo (bienvenida o restablecimiento) para
    // definir su contraseña real y activar/recuperar el acceso a su cuenta.
    @PostMapping("/restablecer/confirmar")
    public ResponseEntity<?> canjearToken(@Valid @RequestBody CanjearTokenRequest datos) {
        try {
            tokenCredencialService.canjearToken(datos.getToken(), datos.getNuevaPassword());
            return ResponseEntity.ok("Contraseña actualizada correctamente. Ya puedes iniciar sesión.");
        } catch (TokenInvalidoException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    private String obtenerIpCliente(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
