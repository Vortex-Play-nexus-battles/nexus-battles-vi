package com.nexusbattles.ms_finanzas.partidas;

import java.util.List;
import java.util.UUID;

/**
 * Respuesta del endpoint {@code POST /partidas/resultado}. Detalla qué
 * jugadores fueron acreditados, cuánto, y a quién se le entregó cofre.
 * Los sancionados se listan aparte para que el llamador (ms-salas-partidas)
 * los pueda mostrar en la UI si quiere ("estos jugadores no ganaron
 * créditos porque tienen una sanción activa").
 */
public record ResultadoPartidaResponse(
        String partidaId,
        List<AcreditacionAplicada> acreditaciones,
        List<String> sancionadosExcluidos) {

    /**
     * @param uid            jugador acreditado
     * @param monto          créditos otorgados (2/4 al ganador, 1 al participante)
     * @param esGanador      true si es el ganador de la partida
     * @param cofreId        id del cofre entregado si superó los 20 créditos
     *                       de la semana con cuota disponible; {@code null}
     *                       si no correspondía cofre
     */
    public record AcreditacionAplicada(
            String uid,
            int monto,
            boolean esGanador,
            UUID cofreId) { }
}
