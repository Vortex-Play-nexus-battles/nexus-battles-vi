package nexus.inventario.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;

/**
 * {@code ElementoInventario} del contrato. Desde 1.6.0 (B9) un HEROE lleva su
 * {@code nivel} y su {@code experiencia} (1 y 0 si aun no ha progresado) y, si
 * esta en una mision, {@code ejecucionMisionId}; en los demas tipos esos campos
 * no aparecen.
 */
public record ElementoInventarioResponse(
        String id,
        String productoId,
        TipoElementoInventario tipo,
        String nombrePropio,
        ParteArmadura parteArmadura,
        boolean disponible,
        String subastaId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer nivel,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double experiencia,
        @JsonInclude(JsonInclude.Include.NON_NULL) String ejecucionMisionId) {

    static ElementoInventarioResponse de(ElementoInventario elemento) {
        boolean heroe = elemento.tipo() == TipoElementoInventario.HEROE;
        return new ElementoInventarioResponse(
                elemento.id(), elemento.productoId(), elemento.tipo(),
                elemento.nombrePropio(), elemento.parteArmadura(),
                elemento.disponible(), elemento.subastaId(),
                heroe ? elemento.nivelActual() : null,
                heroe ? elemento.experienciaActual() : null,
                elemento.ejecucionMisionId());
    }
}
