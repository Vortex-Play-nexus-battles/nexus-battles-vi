package com.nexusbattles.ms_identidad.auth.controller;

import com.nexusbattles.ms_identidad.auth.dto.CambiarPasswordRequest;
import com.nexusbattles.ms_identidad.auth.dto.CambioDePasswordResponse;
import com.nexusbattles.ms_identidad.auth.exception.CambioDePasswordRechazadoException;
import com.nexusbattles.ms_identidad.auth.service.CambioDePasswordService;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code PUT /api/v1/auth/password} — HU-AUT-006: cambiar mi contraseña.
 *
 * <p>Protegido con {@link RequirePermission}: el interceptor valida el JWT
 * (firma, vigencia y versión) y deja el sujeto en {@code usuarioActual}. El
 * cuerpo no identifica a nadie; quien cambia es quien firma el token.
 *
 * <p>Los rechazos salen como problem details (CA-06) con {@code type}
 * estable por motivo, no como texto plano como el resto de este controlador
 * heredado.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class CambioDePasswordController {

    private final CambioDePasswordService servicio;

    public CambioDePasswordController(CambioDePasswordService servicio) {
        this.servicio = servicio;
    }

    @PutMapping("/password")
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<CambioDePasswordResponse> cambiar(@Valid @RequestBody CambiarPasswordRequest datos,
                                                            HttpServletRequest request) {
        String apodo = (String) request.getAttribute("usuarioActual");
        if (apodo == null || apodo.isBlank()) {
            // El interceptor ya cerró la puerta; esto es el cinturón por si
            // alguien registra el controlador sin él.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Hace falta iniciar sesión.");
        }
        return ResponseEntity.ok(servicio.cambiar(apodo, datos, ipDe(request)));
    }

    @ExceptionHandler(CambioDePasswordRechazadoException.class)
    public ResponseEntity<ProblemDetail> rechazado(CambioDePasswordRechazadoException error) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(error.getMotivo().estado()), error.getMessage());
        problema.setType(error.getMotivo().tipo());
        problema.setTitle(error.getMotivo().titulo());
        return ResponseEntity.status(error.getMotivo().estado()).body(problema);
    }

    private static String ipDe(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
