package com.nexusbattles.plataforma.torneos.torneo;

import java.util.UUID;

/** Puerto hacia moderacion-sanciones: un integrante sancionado no se inscribe (RF-TOR-002, CA-02). */
public interface ConsultaDeSanciones {

    /** @throws TorneoRechazado SANCIONES_NO_DISPONIBLES si no se pudo consultar (fail-closed, D-14) */
    boolean sancionado(UUID jugador);
}
