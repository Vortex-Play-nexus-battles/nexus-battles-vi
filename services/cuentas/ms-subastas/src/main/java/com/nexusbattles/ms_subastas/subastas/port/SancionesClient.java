package com.nexusbattles.ms_subastas.subastas.port;

import java.util.UUID;

/** Puerto pendiente del contrato de cumplimiento/moderación. */
public interface SancionesClient {
    boolean tieneSancionActiva(UUID usuarioId);
}
