package nexus.inventario.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import nexus.inventario.aplicacion.EquipadoEn;
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
 *
 * <p>1.6.0 (B9) agrega {@code ejecucionMisionId}, tambien opcional: solo sale
 * mientras el heroe esta en una mision («En mision», seccion 7.8.10), y
 * entonces {@code disponible} es falso.
 *
 * <p>1.7.0 agrega {@code equipadoEn}, opcional: el heroe que lleva puesto el
 * elemento, en las paginas de la vitrina y de la busqueda.
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
        @JsonInclude(JsonInclude.Include.NON_NULL) Double experiencia,
        @JsonInclude(JsonInclude.Include.NON_NULL) String ejecucionMisionId,
        @JsonInclude(JsonInclude.Include.NON_NULL) EquipadoEnResponse equipadoEn) {

    static ElementoInventarioResponse de(ElementoInventario elemento) {
        return de(elemento, null);
    }

    static ElementoInventarioResponse de(ElementoInventario elemento, EquipadoEn equipadoEn) {
        return new ElementoInventarioResponse(
                elemento.id(), elemento.productoId(), elemento.tipo(),
                elemento.nombrePropio(), elemento.parteArmadura(),
                elemento.disponible(), elemento.subastaId(),
                elemento.origen(), elemento.referencia(), elemento.nivel(), elemento.experiencia(),
                elemento.ejecucionMisionId(),
                equipadoEn == null ? null : new EquipadoEnResponse(equipadoEn.heroeId(), equipadoEn.nombreHeroe()));
    }

    /** Esquema {@code EquipadoEn} del contrato (1.7.0). */
    public record EquipadoEnResponse(
            String heroeId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String nombreHeroe) {
    }
}
