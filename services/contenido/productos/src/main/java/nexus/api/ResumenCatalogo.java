package nexus.api;

import java.util.Map;

import nexus.dominio.EstadoProducto;
import nexus.dominio.TipoProducto;

public record ResumenCatalogo(
        long total,
        Map<TipoProducto, Long> porTipo,
        Map<EstadoProducto, Long> porEstado) {
}
