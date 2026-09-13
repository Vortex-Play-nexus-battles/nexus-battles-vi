package com.nexusbattles.ms_subastas.subastas.port;

import java.util.UUID;

/** Puerto para el contexto autenticado; no consulta ni modifica usuarios aquí. */
public interface IdentidadClient {
    Identidad actual();
    record Identidad(UUID usuarioId, boolean esMaestroDeJuego) { }
}
