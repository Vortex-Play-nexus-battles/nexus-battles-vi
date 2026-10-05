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
 * @param combate           su estado en el combate tal como lo devolvio el
 *                          motor (poder, cargas, efectos, acciones...), o
 *                          {@code null} antes de la primera respuesta del motor
 *                          (B7)
 */
public record ParticipanteDePartida(
        UUID idJugador,
        HeroeDeCombate heroe,
        boolean esIA,
        Integer equipo,
        int creditosApostados,
        EstadoDeCombate combate) {

    public ParticipanteDePartida {
        Objects.requireNonNull(idJugador, "Un participante sin identificador no se puede anunciar.");
        if (creditosApostados < 0) {
            throw new IllegalArgumentException("Nadie puede apostar creditos negativos.");
        }
    }

    /** Sin estado de combate todavia: recien sentado a la partida. */
    public ParticipanteDePartida(UUID idJugador, HeroeDeCombate heroe, boolean esIA, Integer equipo,
                                 int creditosApostados) {
        this(idJugador, heroe, esIA, equipo, creditosApostados, null);
    }

    /** El mismo participante con su heroe actualizado tras recibir un golpe. */
    public ParticipanteDePartida conHeroe(HeroeDeCombate heroe) {
        return new ParticipanteDePartida(idJugador, heroe, esIA, equipo, creditosApostados, combate);
    }

    /** El mismo participante, asignado a un equipo del modo cooperativo (HU-SAL-004). */
    public ParticipanteDePartida conEquipo(Integer equipo) {
        return new ParticipanteDePartida(idJugador, heroe, esIA, equipo, creditosApostados, combate);
    }

    /** El mismo participante con el estado de combate que devolvio el motor (B7). */
    public ParticipanteDePartida conCombate(EstadoDeCombate combate) {
        return new ParticipanteDePartida(idJugador, heroe, esIA, equipo, creditosApostados, combate);
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
        return inteligenciaArtificial(id, null);
    }

    /**
     * Participante controlado por la IA, con el heroe con el que combatira.
     *
     * <p><b>De donde sale ese heroe (B7, D-B7-11).</b> §7.6 habla de «un heroe
     * aleatorio controlado por la IA»: {@code IniciarPartida} sortea un
     * prototipo del catalogo de heroes, sin sanadores (no podrian ganar: no
     * infligen dano, §6.1.1), en el nivel del heroe del anfitrion y sin
     * equipamiento. Si el catalogo no responde al empezar, la maquina combate
     * con una copia del heroe del anfitrion a vida completa, que es lo que
     * hacia antes de B7.
     *
     * <p>No apuesta creditos: la maquina no tiene bolsa (RF-JUE-014).
     */
    public static ParticipanteDePartida inteligenciaArtificial(UUID id, HeroeDeCombate heroe) {
        return new ParticipanteDePartida(id, heroe, true, null, 0);
    }
}
