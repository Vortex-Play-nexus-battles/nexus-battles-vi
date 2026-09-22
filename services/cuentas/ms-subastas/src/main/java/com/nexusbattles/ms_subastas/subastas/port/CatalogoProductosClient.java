package com.nexusbattles.ms_subastas.subastas.port;

import com.nexusbattles.ms_subastas.subastas.model.TipoProducto;
import java.util.Optional;
import java.util.UUID;

public interface CatalogoProductosClient {
    Optional<Producto> buscar(UUID productoId);

    record Producto(UUID id, String nombre, TipoProducto tipo, String rareza,
                    String miniaturaUrl, String descripcionCorta, String habilidades,
                    boolean subastable) { }
}
