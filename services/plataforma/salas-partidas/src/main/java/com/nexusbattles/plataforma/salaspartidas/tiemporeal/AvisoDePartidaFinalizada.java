package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida;
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
 * No incluye la recompensa por jugar: esa va en {@code recompensa}.
 *
 * <p><b>{@code recompensa}</b> (HU-JUE-012, 1.4.0) es lo que el libro de
 * creditos acredito por jugar —2/4 al ganador, 1 por participar, cofre si
 * hubo— a cada humano no sancionado. Se omite si el libro no respondio y la
 * recompensa quedo pendiente; entonces el mismo mensaje vuelve a salir, con
 * ella, cuando el reintento entre. Tambien se omite cuando ya se anuncio: el
 * libro no repite el detalle de una partida que ya proceso.
 *
 * <p>{@code ganadores} va vacio cuando la partida acabo sin nadie en pie. Es un
 * empate, y declarar ganador a uno de los dos seria inventarlo: el criterio de
 * desempate es una decision del Product Owner que todavia no esta tomada.
 *
 * <p>{@code equipoGanador} (HU-SAL-004) solo viaja en el modo cooperativo:
 * entonces {@code ganadores} son todos los de ese equipo que quedaron en pie.
 *
 * @param tipo          discriminador del canal
 * @param idPartida     partida que termino
 * @param ganadores     quien quedo en pie; vacio si nadie
 * @param equipoGanador equipo que gano, solo con equipos; ausente si no
 * @param reparto       saldo neto de la apuesta por participante; ausente sin apuesta
 * @param recompensa    creditos por jugar acreditados por el libro; ausente si
 *                      quedo pendiente o ya se anuncio
 */
record AvisoDePartidaFinalizada(String tipo, UUID idPartida, List<UUID> ganadores,
                                @JsonInclude(JsonInclude.Include.NON_NULL) Integer equipoGanador,
                                @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Reparto> reparto,
                                @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Recompensa> recompensa) {

    static final String TIPO = "partida.finalizada";

    /** Una entrada de {@code reparto} del contrato. */
    record Reparto(UUID idJugador, int creditos) {

        static Reparto de(RepartoDeCreditos reparto) {
            return new Reparto(reparto.idJugador(), reparto.creditos());
        }
    }

    /** Una entrada de {@code recompensa} del contrato (HU-JUE-012). */
    record Recompensa(UUID idJugador, int creditos, boolean ganador,
                      @JsonInclude(JsonInclude.Include.NON_NULL) UUID cofre) {
        static Recompensa de(CreditoPorPartida credito) {
            return new Recompensa(credito.idJugador(), credito.creditos(), credito.ganador(), credito.cofre());
        }
    }

    static AvisoDePartidaFinalizada de(Partida partida, List<RepartoDeCreditos> reparto) {
        return de(partida, reparto, List.of());
    }

    static AvisoDePartidaFinalizada de(Partida partida, List<RepartoDeCreditos> reparto,
                                       List<CreditoPorPartida> recompensa) {
        return new AvisoDePartidaFinalizada(TIPO, partida.id(),
                partida.ganadores().stream().map(ParticipanteDePartida::idJugador).toList(),
                partida.equipoGanador().orElse(null),
                reparto == null ? List.of() : reparto.stream().map(Reparto::de).toList(),
                recompensa == null ? List.of() : recompensa.stream().map(Recompensa::de).toList());
    }
}
