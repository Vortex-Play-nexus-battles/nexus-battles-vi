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
 *
 * <p>{@code ganadoresUid} (contrato 1.2.0, HU-JUE-012 CA-02): en una partida
 * por equipos gana todo el equipo, y cada integrante recibe lo del ganador
 * grupal. Si viene, manda; {@code ganadorUid} se conserva para el uno contra
 * uno y por compatibilidad. Los dos vacíos = nadie ganó (empate, o ganó la
 * máquina): todos reciben solo lo de participar.
 */
public record ResultadoPartidaRequest(
        String partidaId,
        TipoPartida tipoPartida,
        String ganadorUid,
        List<String> ganadoresUid,
        List<ParticipantePartidaRequest> participantes) {

    /** Constructor previo al 1.2.0: un solo ganador. */
    public ResultadoPartidaRequest(String partidaId, TipoPartida tipoPartida, String ganadorUid,
                                   List<ParticipantePartidaRequest> participantes) {
        this(partidaId, tipoPartida, ganadorUid, null, participantes);
    }

    /** Quiénes ganaron, mirando primero la lista y después el campo simple. */
    public List<String> ganadores() {
        if (ganadoresUid != null && !ganadoresUid.isEmpty()) {
            return ganadoresUid;
        }
        return ganadorUid == null ? List.of() : List.of(ganadorUid);
    }

    /** Lo mínimo para que el informe tenga sentido; sin esto el 500 tapaba un 400. */
    public void validar() {
        if (partidaId == null || partidaId.isBlank()) {
            throw new IllegalArgumentException("partidaId es obligatorio");
        }
        if (tipoPartida == null) {
            throw new IllegalArgumentException("tipoPartida es obligatorio (UNO_A_UNO o GRUPAL)");
        }
        if (participantes == null || participantes.isEmpty()) {
            throw new IllegalArgumentException("participantes no puede estar vacío");
        }
        for (ParticipantePartidaRequest p : participantes) {
            if (p == null || p.uid() == null || p.uid().isBlank()) {
                throw new IllegalArgumentException("todo participante lleva uid");
            }
        }
        for (String ganador : ganadores()) {
            if (participantes.stream().noneMatch(p -> ganador.equals(p.uid()))) {
                throw new IllegalArgumentException("el ganador " + ganador + " no está entre los participantes");
            }
        }
    }

    public record ParticipantePartidaRequest(
            String uid,
            boolean sancionado) { }
}
