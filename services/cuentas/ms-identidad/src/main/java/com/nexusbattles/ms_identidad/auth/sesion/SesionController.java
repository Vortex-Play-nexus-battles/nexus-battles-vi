package com.nexusbattles.ms_identidad.auth.sesion;

import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code POST /api/v1/auth/sesion/renovacion} (ms-identidad-auth.yaml 2.3.0):
 * la sesion en uso recibe un token nuevo con la misma autenticacion. Ver
 * {@link RenovacionDeSesionService}.
 */
@RestController
@RequestMapping("/api/v1/auth/sesion")
public class SesionController {

    private final RenovacionDeSesionService renovacion;

    public SesionController(RenovacionDeSesionService renovacion) {
        this.renovacion = renovacion;
    }

    @PostMapping("/renovacion")
    public ResponseEntity<Map<String, Object>> renovar(HttpServletRequest peticion) {
        RenovacionDeSesionService.SesionRenovada sesion = renovacion.renovar(
                peticion.getHeader(HttpHeaders.AUTHORIZATION));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("token", sesion.token(), "expiraEn", sesion.expiraEn().toString()));
    }

    @ExceptionHandler(SesionNoRenovableException.class)
    public ResponseEntity<ProblemDetail> noRenovable(SesionNoRenovableException error, HttpServletRequest peticion) {
        return Problemas.de(HttpStatus.UNAUTHORIZED, "sesion-no-renovable", "Sesión no renovable",
                error.getMessage(), peticion.getRequestURI());
    }
}
