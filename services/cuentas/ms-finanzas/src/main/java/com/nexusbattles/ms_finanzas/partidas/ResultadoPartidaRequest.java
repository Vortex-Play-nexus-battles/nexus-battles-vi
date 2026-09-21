package com.nexusbattles.ms_finanzas.partidas;

import java.util.List;

/**
 * Entrada del endpoint {@code POST /partidas/resultado}. Lo consume
 * ms-salas-partidas al finalizar una partida — por eso el {@code partidaId}
 * lo genera ese servicio, y este servicio solo lo usa como clave de
 * idempotencia. {@code ganadorUid} puede ser {@code null} en el hipotético
 * caso de un empate/abandono; entonces todos los participantes no
 * sancionados reciben solo el crédito por participar.
 *
 * <p>El campo {@code sancionado} viene desde ms-plataforma/moderación:
 * ms-finanzas no consulta la lista de sancionados por su cuenta (regla 7).
 */
public record ResultadoPartidaRequest(
        String partidaId,
        TipoPartida tipoPartida,
        String ganadorUid,
        List<ParticipantePartidaRequest> participantes) {

    public record ParticipantePartidaRequest(
            String uid,
            boolean sancionado) { }
}
