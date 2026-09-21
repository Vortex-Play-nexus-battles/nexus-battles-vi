package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos;

import java.util.List;
import java.util.UUID;

/**
 * Mensaje {@code partida.finalizada} — HU-JUE-005, RF-JUE-017, HU-JUE-014.
 *
 * <p>Espejo del mensaje {@code PartidaFinalizada} del AsyncAPI.
 *
 * <p><b>{@code reparto}</b> es el resultado economico de la <b>apuesta</b>
 * (HU-JUE-014, CA-04): cuantos creditos netos gano o perdio cada participante.
 * Se omite cuando la partida no tenia apuesta y tambien cuando el libro de
 * creditos no respondio y la liquidacion quedo pendiente; en ese caso el
 * mismo mensaje vuelve a salir, ya con reparto, cuando el reintento la cierre.
 * No incluye la recompensa por jugar (2/4 al ganador, 1 por participar):
 * esa es de HU-JUE-012 y la reparte ms-finanzas.
 *
 * <p>{@code ganadores} va vacio cuando la partida acabo sin nadie en pie. Es un
 * empate, y declarar ganador a uno de los dos seria inventarlo: el criterio de
 * desempate es una decision del Product Owner que todavia no esta tomada.
 *
 * @param tipo      discriminador del canal
 * @param idPartida partida que termino
 * @param ganadores quien quedo en pie; vacio si nadie
 * @param reparto   saldo neto de la apuesta por participante; ausente sin apuesta
 */
record AvisoDePartidaFinalizada(String tipo, UUID idPartida, List<UUID> ganadores,
                                @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Reparto> reparto) {

    static final String TIPO = "partida.finalizada";

    /** Una entrada de {@code reparto} del contrato. */
    record Reparto(UUID idJugador, int creditos) {

        static Reparto de(RepartoDeCreditos reparto) {
            return new Reparto(reparto.idJugador(), reparto.creditos());
        }
    }

    static AvisoDePartidaFinalizada de(Partida partida, List<RepartoDeCreditos> reparto) {
        return new AvisoDePartidaFinalizada(TIPO, partida.id(),
                partida.ganador().map(ParticipanteDePartida::idJugador).map(List::of)
                        .orElseGet(List::of),
                reparto == null ? List.of() : reparto.stream().map(Reparto::de).toList());
    }
}
