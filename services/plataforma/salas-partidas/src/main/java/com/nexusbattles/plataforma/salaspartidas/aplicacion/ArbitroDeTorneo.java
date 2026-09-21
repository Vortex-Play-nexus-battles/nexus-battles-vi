package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import java.util.UUID;

/**
 * Puerto de salida hacia torneos: {@code POST /torneos/{id}/encuentros/{n}/resultado}
 * con {@code ganadorUid} (contracts/openapi/torneos.yaml 1.1.0). Lo llama
 * este servicio con su credencial (ROLE_SERVICIO): es «la partida jugada»
 * que RF-TOR-004 exige como unica fuente del resultado, junto con el
 * administrador con motivo.
 */
public interface ArbitroDeTorneo {

    /**
     * @throws TorneoNoDisponible si torneos no responde o rechaza (se anota y se puede reintentar)
     */
    void informarGanador(UUID idTorneo, int numero, UUID ganadorUid, UUID idPartida);

    class TorneoNoDisponible extends RuntimeException {
        public TorneoNoDisponible(String detalle) {
            super(detalle);
        }
    }
}
