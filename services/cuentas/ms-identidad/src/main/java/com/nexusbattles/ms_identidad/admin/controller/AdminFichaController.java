package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.ficha.FichaAdministrativa;
import com.nexusbattles.ms_identidad.admin.ficha.FichaAdministrativaService;
import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import com.nexusbattles.ms_identidad.sanciones.CuentaNoEncontradaException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-USR-010 — {@code GET /api/v1/admin/usuarios/{usuario}/ficha}
 * (ms-identidad-admin.yaml 1.4.0).
 *
 * <p>Aparte de {@link AdminGestionUsuarioController} porque no gestiona nada:
 * es una lectura, pero una que deja rastro en la auditoria
 * ({@link FichaAdministrativaService}). Mismo permiso que el directorio y la
 * gestion: quien puede buscar una cuenta y editarla puede ver su ficha.
 */
@RestController
@RequestMapping("/api/v1/admin/usuarios")
public class AdminFichaController {

    private final FichaAdministrativaService fichas;

    public AdminFichaController(FichaAdministrativaService fichas) {
        this.fichas = fichas;
    }

    /** Sin cache en ningun intermediario: son datos personales. */
    @GetMapping("/{usuario}/ficha")
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public ResponseEntity<FichaAdministrativa> consultar(@PathVariable("usuario") String usuario,
                                                         HttpServletRequest request) {
        String administrador = (String) request.getAttribute("usuarioActual");
        FichaAdministrativa ficha = fichas.consultar(usuario, administrador,
                AdminGestionUsuarioController.obtenerIpReal(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ficha);
    }

    /** Usuario inexistente (CA-03): problem details, no el texto plano heredado del resto del panel. */
    @ExceptionHandler(CuentaNoEncontradaException.class)
    public ResponseEntity<ProblemDetail> noEncontrada(CuentaNoEncontradaException error, HttpServletRequest request) {
        return Problemas.de(HttpStatus.NOT_FOUND, "cuenta-no-encontrada", "Cuenta no encontrada",
                error.getMessage(), request.getRequestURI());
    }
}
