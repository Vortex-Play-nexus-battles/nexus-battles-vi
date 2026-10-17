package com.nexusbattles.ms_identidad.rbac.controller;

import com.nexusbattles.ms_identidad.admin.service.UsuarioNoEncontradoException;
import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.rbac.dto.AsignarRolRequest;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import com.nexusbattles.ms_identidad.rbac.service.RoleAssignmentService;
import com.nexusbattles.ms_identidad.rbac.service.UltimoSuperAdministradorException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/rbac/usuarios")
public class RoleAssignmentController {

    private final RoleAssignmentService roleAssignmentService;
    private final JwtService jwtService;

    public RoleAssignmentController(
        RoleAssignmentService roleAssignmentService,
        JwtService jwtService) {

        this.roleAssignmentService = roleAssignmentService;
        this.jwtService = jwtService;
    }

    @PutMapping("/{usuarioId}/rol")
    @RequirePermission(Action.ASIGNAR_ROL)
    public ResponseEntity<?> asignarRol(
        @PathVariable Long usuarioId,
        @Valid @RequestBody AsignarRolRequest datos,
        HttpServletRequest request) {

        String administradorId =
            obtenerAdministrador(request);

        String ipOrigen =
            obtenerIpCliente(request);

        Object uidActor = request.getAttribute("uidActual");
        roleAssignmentService.asignarRol(
            usuarioId,
            datos.getNuevoRol(),
            administradorId,
            ipOrigen,
            datos.getMotivo(),
            uidActor == null ? null : uidActor.toString()
        );

        return ResponseEntity.ok(
            Map.of(
                "mensaje",
                "Rol actualizado correctamente"
            )
        );
    }

    /** rbac.yaml: 404 si la cuenta no existe (antes salia un 500 sin cuerpo). */
    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ResponseEntity<ProblemDetail> noEncontrada(RuntimeException error, HttpServletRequest request) {
        return Problemas.de(HttpStatus.NOT_FOUND, "cuenta-no-encontrada", "Cuenta no encontrada",
            error.getMessage(), request.getRequestURI());
    }

    /** 409: quedaria el sistema sin Super Administrador. */
    @ExceptionHandler(UltimoSuperAdministradorException.class)
    public ResponseEntity<ProblemDetail> ultimoSuper(RuntimeException error, HttpServletRequest request) {
        return Problemas.de(HttpStatus.CONFLICT, "ultimo-super-administrador", "Último Super Administrador",
            error.getMessage(), request.getRequestURI());
    }

    private String obtenerAdministrador(
        HttpServletRequest request) {

        String authorization =
            request.getHeader("Authorization");

        if (authorization != null
            && authorization.startsWith("Bearer ")) {

            String token =
                authorization.substring(7).trim();

            Claims claims =
                jwtService.validarYObtenerClaims(token);

            String subject =
                claims.getSubject();

            if (subject != null
                && !subject.isBlank()) {
                return subject;
            }
        }

        /*
         * Respaldo temporal para pruebas/demo mientras
         * X-User-Name siga soportado por SecurityInterceptor.
         */
        String username =
            request.getHeader("X-User-Name");

        if (username != null
            && !username.isBlank()) {
            return username;
        }

        throw new IllegalStateException(
            "No se pudo identificar al administrador solicitante."
        );
    }

    private String obtenerIpCliente(
        HttpServletRequest request) {

        String forwarded =
            request.getHeader("X-Forwarded-For");

        if (forwarded != null
            && !forwarded.isBlank()) {

            return forwarded
                .split(",")[0]
                .trim();
        }

        return request.getRemoteAddr();
    }
}
