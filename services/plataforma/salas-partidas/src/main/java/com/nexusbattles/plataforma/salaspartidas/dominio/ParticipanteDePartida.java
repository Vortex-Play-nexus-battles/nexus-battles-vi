package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Quien combate en una partida — RF-JUE-017, espejo de {@code Participante} en
 * {@code contracts/openapi/salas-partidas.yaml}.
 *
 * <p>{@code heroe} puede faltar. La verificacion previa de HU-SAL-003 pregunta
 * al inventario con que heroe entraria cada jugador, pero mientras esa
 * comprobacion no sea obligatoria en el ingreso hay participantes de los que
 * este servicio no conoce el heroe. Anunciar la partida sin heroe es honesto;
 * inventar uno para rellenar la barra de vida, no.
 *
 * <p>{@code esIA} sale de {@code incluirHeroeIA} de la sala (RF-JUE-001). El
 * participante de la IA no tiene identificador de jugador real, asi que se le
 * asigna uno propio al iniciar: el contrato exige {@code jugador} en todos.
 *
 * @param idJugador         identificador estable del jugador, o el de la IA
 * @param heroe             heroe con el que combate, o {@code null}
 * @param esIA              si lo controla la inteligencia artificial
 * @param equipo            numero de equipo en modalidad cooperativa, o {@code null}
 * @param creditosApostados creditos que este participante puso en juego
 */
public record ParticipanteDePartida(
        UUID idJugador,
        HeroeDeCombate heroe,
        boolean esIA,
        Integer equipo,
        int creditosApostados) {

    public ParticipanteDePartida {
        Objects.requireNonNull(idJugador, "Un participante sin identificador no se puede anunciar.");
        if (creditosApostados < 0) {
            throw new IllegalArgumentException("Nadie puede apostar creditos negativos.");
        }
    }

    /** Participante humano sin heroe conocido todavia. */
    /** El mismo participante con su heroe actualizado tras recibir un golpe. */
    public ParticipanteDePartida conHeroe(HeroeDeCombate heroe) {
        return new ParticipanteDePartida(idJugador, heroe, esIA, equipo, creditosApostados);
    }

    /**
     * True cuando sigue en pie.
     *
     * <p>Un participante sin heroe conocido cuenta como vivo: no se le puede
     * dar por derrotado por una integracion que no llego. Lo que no se sabe no
     * se decide.
     */
    public boolean enPie() {
        return heroe == null || !heroe.derrotado();
    }

    public static ParticipanteDePartida humano(UUID idJugador, int creditosApostados) {
        return new ParticipanteDePartida(idJugador, null, false, null, creditosApostados);
    }

    /**
     * Participante controlado por la IA (RF-JUE-001).
     *
     * <p>Recibe un identificador propio porque el contrato exige {@code jugador}
     * en cada participante y la vista de combate necesita distinguirlo de los
     * demas para dirigirle las acciones.
     */
    public static ParticipanteDePartida inteligenciaArtificial(UUID id) {
        return new ParticipanteDePartida(id, null, true, null, 0);
    }
}
