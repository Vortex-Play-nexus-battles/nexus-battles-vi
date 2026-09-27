package nexus.inventario.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.OrigenDeEntrega;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;

/**
 * Esquema {@code ElementoInventario} del contrato.
 *
 * <p>B4 agrega cuatro campos opcionales, que solo salen cuando tienen valor
 * (los consumidores anteriores no los esperan y los que ignoran campos
 * desconocidos siguen igual): {@code origen} y {@code referencia} en lo que
 * llego por una entrega, {@code nivel} y {@code experiencia} en los heroes.
 */
public record ElementoInventarioResponse(
        String id,
        String productoId,
        TipoElementoInventario tipo,
        String nombrePropio,
        ParteArmadura parteArmadura,
        boolean disponible,
        String subastaId,
        @JsonInclude(JsonInclude.Include.NON_NULL) OrigenDeEntrega origen,
        @JsonInclude(JsonInclude.Include.NON_NULL) String referencia,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer nivel,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double experiencia) {

    static ElementoInventarioResponse de(ElementoInventario elemento) {
        return new ElementoInventarioResponse(
                elemento.id(), elemento.productoId(), elemento.tipo(),
                elemento.nombrePropio(), elemento.parteArmadura(),
                elemento.disponible(), elemento.subastaId(),
                elemento.origen(), elemento.referencia(), elemento.nivel(), elemento.experiencia());
    }
}
