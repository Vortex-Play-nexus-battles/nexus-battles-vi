package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mensaje {@code partida.turno.cambiado} — RF-JUE-017.
 *
 * <p>Se publica cada vez que el turno pasa de manos. Va aparte del aviso de
 * accion resuelta porque el turno tambien cambia sin accion —abandono, tiempo
 * agotado— y la vista de combate tiene que enterarse igual.
 *
 * <p>Desde 1.5.0 (B7) trae el estado de quien juega ahora YA con lo que ocurre
 * al empezar su turno (+2 de poder, efectos por turno): su poder, sus cargas,
 * sus efectos y las acciones que puede jugar, calculadas por el motor.
 *
 * @param tipo              discriminador del canal
 * @param idPartida         partida afectada
 * @param idJugador         a quien le toca ahora
 * @param numeroTurno       turno en curso, desde 1
 * @param segundosParaJugar cuenta atras, o nula sin tiempo por turno (D-B7-14)
 * @param motivo            ACCION, TIEMPO_AGOTADO o TURNO_PERDIDO
 */
record AvisoDeTurno(String tipo, UUID idPartida, UUID idJugador, int numeroTurno, Integer segundosParaJugar,
                    String motivo, Integer poderActual, Integer poderMaximo, Map<String, Integer> recargas,
                    List<EfectoEnCable> efectosActivos, List<EstadoDeCombate.AccionDisponible> acciones) {

    static final String TIPO = "partida.turno.cambiado";

    static AvisoDeTurno de(Partida partida) {
        return de(partida, null, Instant.now());
    }

    static AvisoDeTurno de(Partida partida, String motivo, Instant ahora) {
        UUID enTurno = partida.turnoActual().idJugador();
        EstadoDeCombate combate = partida.participante(enTurno).map(p -> p.combate()).orElse(null);
        Integer segundos = partida.turnoVenceEn() == null ? null
                : (int) Math.max(0, Duration.between(ahora, partida.turnoVenceEn()).toSeconds());
        return new AvisoDeTurno(TIPO, partida.id(), enTurno, partida.turnoActual().numeroTurno(), segundos, motivo,
                combate == null ? null : combate.poderActual(),
                combate == null ? null : combate.poderMaximo(),
                combate == null ? Map.of() : combate.recargas(),
                combate == null ? List.of() : EfectoEnCable.de(combate.efectos()),
                combate == null ? List.of() : combate.acciones());
    }
}
