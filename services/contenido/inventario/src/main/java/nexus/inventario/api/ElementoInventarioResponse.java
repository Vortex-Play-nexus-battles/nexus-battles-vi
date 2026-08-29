package nexus.inventario.api;

import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.TipoElementoInventario;

public record ElementoInventarioResponse(
        String id,
        String productoId,
        TipoElementoInventario tipo,
        String nombrePropio) {

    static ElementoInventarioResponse de(ElementoInventario elemento) {
        return new ElementoInventarioResponse(
                elemento.id(), elemento.productoId(), elemento.tipo(), elemento.nombrePropio());
    }
}
