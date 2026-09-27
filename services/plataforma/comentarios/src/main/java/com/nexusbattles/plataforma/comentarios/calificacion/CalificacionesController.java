package com.nexusbattles.plataforma.comentarios.calificacion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import com.nexusbattles.plataforma.comentarios.Calificacion;
import com.nexusbattles.plataforma.comentarios.ResumenDeCalificaciones;

/**
 * La calificacion de un producto, sin escribir nada — contrato 1.4.0
 * ({@code /products/{productId}/rating}), B3.
 *
 * <p>Cuelga del producto, igual que sus comentarios, porque es donde esta el
 * jugador cuando califica: en la ficha. Quien califica sale del token
 * ({@code uid}); el cuerpo solo trae las estrellas. El resumen es publico —la
 * vitrina y la ficha lo ensenan sin sesion— y {@code /mia} es de cada uno.
 *
 * <p>Las reglas estan en {@link ServicioDeCalificaciones}; aqui solo se
 * traduce HTTP. Los rechazos salen como problem details por
 * {@code ManejadorErroresComentarios}.
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/rating")
public class CalificacionesController {

    private final ServicioDeCalificaciones servicio;

    public CalificacionesController(ServicioDeCalificaciones servicio) {
        this.servicio = servicio;
    }

    /** Promedio, total y distribucion. Publico. */
    @GetMapping
    public ResumenResponse resumen(@PathVariable String productId) {
        return ResumenResponse.desde(servicio.resumenPublico(productId));
    }

    /** Califica una sola vez: 201, o 409 si ya lo hizo. */
    @PostMapping
    public ResponseEntity<CalificacionResponse> calificar(
            @PathVariable String productId,
            @AuthenticationPrincipal Jwt autor,
            @RequestBody CalificacionRequest peticion) {

        ServicioDeCalificaciones.Calificado calificado = servicio.calificar(
                productId, IdentidadDelToken.idDe(autor).toString(), peticion.estrellas());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CalificacionResponse.desde(calificado.calificacion(), calificado.resumen()));
    }

    /** La propia; 404 si todavia no califico. */
    @GetMapping("/mia")
    public CalificacionResponse mia(@PathVariable String productId, @AuthenticationPrincipal Jwt autor) {
        Calificacion propia = servicio.de(productId, IdentidadDelToken.idDe(autor).toString())
                .orElseThrow(() -> new CalificacionNoEncontrada(productId));
        return CalificacionResponse.desde(propia, null);
    }

    // ------------------------------------------------------------------ DTOs

    /** {@code CalificacionRequest} del contrato. */
    public record CalificacionRequest(Integer estrellas) {
    }

    /**
     * {@code CalificacionResponse} del contrato. {@code resumen} solo viaja al
     * calificar, que es cuando el cliente lo necesita para repintar la ficha
     * sin otra peticion.
     */
    public record CalificacionResponse(
            String productoId,
            int estrellas,
            Instant fecha,
            @JsonInclude(JsonInclude.Include.NON_NULL) ResumenResponse resumen) {

        static CalificacionResponse desde(Calificacion calificacion, ResumenDeCalificaciones resumen) {
            return new CalificacionResponse(
                    calificacion.productoId(),
                    calificacion.estrellas(),
                    calificacion.creadaEn(),
                    resumen == null ? null : ResumenResponse.desde(resumen));
        }
    }

    /**
     * {@code ResumenDeCalificaciones} del contrato: la distribucion con las
     * cinco claves siempre, de la 1 a la 5, aunque alguna valga cero.
     */
    public record ResumenResponse(String productoId, Double promedio, long total, Map<String, Long> distribucion) {

        static ResumenResponse desde(ResumenDeCalificaciones resumen) {
            Map<String, Long> distribucion = new LinkedHashMap<>();
            resumen.distribucion().forEach((estrellas, cantidad) ->
                    distribucion.put(String.valueOf(estrellas), cantidad));
            return new ResumenResponse(resumen.productoId(), resumen.promedio(), resumen.total(), distribucion);
        }
    }
}
