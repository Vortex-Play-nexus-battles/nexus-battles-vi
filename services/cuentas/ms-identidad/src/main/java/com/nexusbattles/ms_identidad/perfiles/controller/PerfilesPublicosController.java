package com.nexusbattles.ms_identidad.perfiles.controller;

import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import com.nexusbattles.ms_identidad.perfiles.dto.PerfilPublicoResponse;
import com.nexusbattles.ms_identidad.perfiles.service.BusquedaDeJugadores;
import com.nexusbattles.ms_identidad.perfiles.service.BusquedaInvalidaException;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code GET /api/v1/perfiles/publicos?apodo=} — buscar a quien escribir un
 * mensaje privado (B6, feedback del profesor; ms-identidad-perfiles.yaml).
 *
 * <p><b>Por que no esta en {@link PerfilController}.</b> Aquel sirve el perfil
 * PROPIO, completo y solo a su dueno. Esto sirve datos PUBLICOS de otros
 * ({@code uid}, apodo, avatar) a cualquiera con sesion. Mezclarlos obligaria
 * a mirar en cada cambio futuro cual de las dos reglas de acceso se esta
 * tocando.
 *
 * <p><b>Que no se pisa con {@code /api/v1/perfiles/{usuario}}.</b> Spring
 * elige el patron mas especifico, y un segmento literal ({@code publicos})
 * gana a una variable ({@code {usuario}}): esta ruta nunca llega a
 * {@code PerfilController} como si «publicos» fuera un uid. El interceptor
 * tampoco las confunde: decide por la anotacion del metodo que resolvio
 * Spring, no por la ruta. Lo prueban {@code PerfilesPublicosControllerTest}
 * (los dos controladores montados a la vez) y {@code PerfilesPublicosIT}.
 *
 * <p><b>Permiso.</b> {@link Action#MODIFICAR_PERFIL_PROPIO}, el que los
 * cuatro roles tienen concedido en la Tabla 24 y el que este servicio ya usa
 * para «cualquier sesion valida» (cerrar sesion, cambiar la contrasena,
 * preguntas de seguridad, el alta, leer el perfil propio). El contrato pide
 * exactamente eso: sesion de cualquier rol; sin token, token invalido o
 * revocado, 403 del {@code SecurityInterceptor} (fail-closed). No se anade
 * una accion nueva a la matriz: la Tabla 24 es del documento del curso y esta
 * busqueda no le quita ni le da nada a ningun rol.
 */
@RestController
@RequestMapping("/api/v1/perfiles/publicos")
public class PerfilesPublicosController {

    private final BusquedaDeJugadores busqueda;

    public PerfilesPublicosController(BusquedaDeJugadores busqueda) {
        this.busqueda = busqueda;
    }

    /**
     * El parametro es opcional para Spring a proposito: si falta, lo rechaza
     * {@link BusquedaDeJugadores} con el mismo 400 {@code datos-invalidos} que
     * un texto corto, en vez del 400 generico de Spring con otro formato.
     */
    @GetMapping
    @RequirePermission(Action.MODIFICAR_PERFIL_PROPIO)
    public ResponseEntity<List<PerfilPublicoResponse>> buscar(
            @RequestParam(name = "apodo", required = false) String apodo) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(busqueda.buscar(apodo));
    }

    @ExceptionHandler(BusquedaInvalidaException.class)
    public ResponseEntity<ProblemDetail> invalida(BusquedaInvalidaException error, HttpServletRequest peticion) {
        return Problemas.de(HttpStatus.BAD_REQUEST, "datos-invalidos", "Búsqueda inválida",
                error.getMessage(), peticion.getRequestURI());
    }
}
