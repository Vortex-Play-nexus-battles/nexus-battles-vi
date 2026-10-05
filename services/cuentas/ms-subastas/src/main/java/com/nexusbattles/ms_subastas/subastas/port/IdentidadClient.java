package com.nexusbattles.ms_subastas.subastas.port;

import java.util.UUID;

/** Puerto para el contexto autenticado; no consulta ni modifica usuarios aquí. */
public interface IdentidadClient {
    Identidad actual();

    /**
     * @param usuarioId        el {@code uid} estable del token (ADR-002)
     * @param esMaestroDeJuego si es la cuenta «Maestro de Juego» de 7.7.4. Hoy
     *                         siempre falso: ms-identidad no tiene ese rol ni
     *                         esa marca (HU-SUB-010), y no se inventa aqui
     * @param apodo            el apodo con el que entro (claim {@code sub}); solo para
     *                         mostrar —«nueva puja de a***s»—, nunca como clave
     */
    record Identidad(UUID usuarioId, boolean esMaestroDeJuego, String apodo) {

        /** Sin apodo: las pruebas y quien solo necesita la identidad. */
        public Identidad(UUID usuarioId, boolean esMaestroDeJuego) {
            this(usuarioId, esMaestroDeJuego, null);
        }
    }
}
