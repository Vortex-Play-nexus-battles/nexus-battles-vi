package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.inventario.CuentaSinInventarioException;
import com.nexusbattles.ms_identidad.admin.inventario.EntregaAdministrativaRequest;
import com.nexusbattles.ms_identidad.admin.inventario.EntregaAdministrativaResponse;
import com.nexusbattles.ms_identidad.admin.inventario.EntregaAdministrativaService;
import com.nexusbattles.ms_identidad.admin.inventario.EntregaRechazadaException;
import com.nexusbattles.ms_identidad.admin.inventario.InventarioNoDisponibleException;
import com.nexusbattles.ms_identidad.admin.service.UsuarioNoEncontradoException;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Modificar el inventario de una cuenta desde la consola — SA-10, §7.3.4
 * («ver y modificar inventario del usuario»), ms-identidad-admin.yaml 1.10.0.
 *
 * <p>Administrador o Super Administrador ({@code GESTIONAR_CUENTAS}); la
 * jerarquia de 7.3.1, el orden inventario → auditoria y la idempotencia los
 * explica {@link EntregaAdministrativaService}. Todos los rechazos de esta
 * ruta salen en problem details (regla 4); los de jerarquia y auditoria, con
 * los de todo el panel ({@code AdministracionAdvice}).
 */
@RestController
@RequestMapping("/api/v1/admin/usuarios")
public class AdminInventarioController {

    static final String ERRORES = "https://nexusbattles.upb.edu.co/errors/";

    private final EntregaAdministrativaService entregas;

    public AdminInventarioController(EntregaAdministrativaService entregas) {
        this.entregas = entregas;
    }

    @PostMapping(value = "/{usuarioId}/inventario/entregas", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public ResponseEntity<EntregaAdministrativaResponse> entregar(
            @PathVariable Long usuarioId,
            @RequestHeader(value = "Idempotency-Key", required = false) String clave,
            @Valid @RequestBody EntregaAdministrativaRequest datos,
            HttpServletRequest request) {
        Object rol = request.getAttribute("rolActual");
        EntregaAdministrativaResponse entrega = entregas.entregar(usuarioId, clave, datos,
                request.getHeader(HttpHeaders.AUTHORIZATION), (String) request.getAttribute("usuarioActual"),
                rol == null ? null : rol.toString(), AdminGestionUsuarioController.obtenerIpReal(request));
        return ResponseEntity.status(entrega.nueva() ? HttpStatus.CREATED : HttpStatus.OK).body(entrega);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> invalido(Exception error, HttpServletRequest request) {
        String detalle = error instanceof IllegalArgumentException
                ? error.getMessage()
                : "Revisa el producto, la cantidad (de 1 a 20) y el motivo (de 3 a 500 caracteres).";
        return problema(HttpStatus.BAD_REQUEST, "datos-invalidos", "Datos inválidos", detalle, request, null);
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    ResponseEntity<ProblemDetail> sinCuenta(UsuarioNoEncontradoException error, HttpServletRequest request) {
        return problema(HttpStatus.NOT_FOUND, "cuenta-no-encontrada", "Cuenta no encontrada", error.getMessage(),
                request, null);
    }

    @ExceptionHandler(CuentaSinInventarioException.class)
    ResponseEntity<ProblemDetail> sinInventario(CuentaSinInventarioException error, HttpServletRequest request) {
        return problema(HttpStatus.CONFLICT, "cuenta-sin-inventario", "Cuenta sin inventario", error.getMessage(),
                request, null);
    }

    @ExceptionHandler(EntregaRechazadaException.class)
    ResponseEntity<ProblemDetail> rechazada(EntregaRechazadaException error, HttpServletRequest request) {
        HttpStatus estado = switch (error.getEstado()) {
            case 401, 403 -> HttpStatus.FORBIDDEN;
            case 404, 422 -> HttpStatus.valueOf(422);
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return problema(estado, "entrega-rechazada", "El inventario rechazó la entrega", error.getMessage(),
                request, null);
    }

    @ExceptionHandler(InventarioNoDisponibleException.class)
    ResponseEntity<ProblemDetail> noDisponible(InventarioNoDisponibleException error, HttpServletRequest request) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "inventario-no-disponible", "Inventario no disponible",
                error.getMessage(), request, InventarioNoDisponibleException.REINTENTAR_EN_SEGUNDOS);
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String tipo, String titulo,
                                                          String detalle, HttpServletRequest request,
                                                          Integer reintentarEn) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create(ERRORES + tipo));
        problema.setTitle(titulo);
        problema.setInstance(URI.create(request.getRequestURI()));
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(estado)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (reintentarEn != null) {
            respuesta.header(HttpHeaders.RETRY_AFTER, String.valueOf(reintentarEn));
        }
        return respuesta.body(problema);
    }
}
