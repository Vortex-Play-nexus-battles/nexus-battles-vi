package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;

import java.util.List;
import java.util.UUID;

/**
 * Mensaje {@code partida.finalizada} — HU-JUE-005, RF-JUE-017.
 *
 * <p>Espejo del mensaje {@code PartidaFinalizada} del AsyncAPI.
 *
 * <p><b>Sin {@code reparto}.</b> El contrato lo declara opcional y este servicio
 * no lo emite: quien reparte creditos y cofres es la economia del juego
 * (HU-JUE-012, {@code ms-finanzas}). Aqui se sabe QUIEN gano, no CUANTO le
 * corresponde. Calcularlo seria inventar la regla de otro modulo y divergiria
 * el dia que esa HU la cambie.
 *
 * <p>{@code ganadores} va vacio cuando la partida acabo sin nadie en pie. Es un
 * empate, y declarar ganador a uno de los dos seria inventarlo: el criterio de
 * desempate es una decision del Product Owner que todavia no esta tomada.
 *
 * @param tipo      discriminador del canal
 * @param idPartida partida que termino
 * @param ganadores quien quedo en pie; vacio si nadie
 */
record AvisoDePartidaFinalizada(String tipo, UUID idPartida, List<UUID> ganadores) {

    static final String TIPO = "partida.finalizada";

    static AvisoDePartidaFinalizada de(Partida partida) {
        return new AvisoDePartidaFinalizada(TIPO, partida.id(),
                partida.ganador().map(ParticipanteDePartida::idJugador).map(List::of)
                        .orElseGet(List::of));
    }
}
