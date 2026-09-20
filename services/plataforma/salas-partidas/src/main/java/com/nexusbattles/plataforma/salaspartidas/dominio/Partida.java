package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Combate en curso — RF-JUE-017, HU-SAL-004.
 *
 * <p>Nace de una sala y no de la nada: {@link #iniciar} toma la sala, copia
 * quien esta dentro y fija el primer turno. La sala sigue siendo la duena del
 * <i>aforo</i> y de <i>quien puede entrar</i>; la partida es duena del
 * <i>estado del combate</i>.
 *
 * <p><b>Que NO decide esta clase.</b> Nada de reglas de juego: ni dano, ni
 * efectos, ni quien gana. Eso es del motor de combate, que el Project Charter
 * excluye de este bloque. Aqui vive el ciclo de vida —iniciar, avanzar el
 * turno, terminar— y el estado que la vista de combate necesita pintar y que
 * hace falta para reconectar tras una caida del canal.
 *
 * <p><b>El orden de los participantes es el orden de los turnos.</b> Se
 * conserva tal como venia de la sala: el anfitrion primero, porque es quien la
 * creo, y detras los demas en el orden en que entraron. Un orden aleatorio
 * seria una regla de juego, y esa no es nuestra.
 */
public class Partida {

    private final UUID id;
    private final UUID idSala;
    private final List<ParticipanteDePartida> participantes;
    private final int recompensaEnJuego;
    private final Instant iniciadaEn;

    private EstadoPartida estado;
    private Turno turnoActual;

    private Partida(UUID id, UUID idSala, List<ParticipanteDePartida> participantes,
                    int recompensaEnJuego, Instant iniciadaEn, EstadoPartida estado,
                    Turno turnoActual) {
        this.id = Objects.requireNonNull(id, "Una partida necesita identificador.");
        this.idSala = Objects.requireNonNull(idSala, "Una partida sale de una sala.");
        this.participantes = new ArrayList<>(
                Objects.requireNonNull(participantes, "Una partida sin participantes no es un combate."));
        if (this.participantes.isEmpty()) {
            throw new IllegalArgumentException("Una partida sin participantes no es un combate.");
        }
        if (recompensaEnJuego < 0) {
            throw new IllegalArgumentException("La recompensa en juego no puede ser negativa.");
        }
        this.recompensaEnJuego = recompensaEnJuego;
        this.iniciadaEn = Objects.requireNonNull(iniciadaEn, "Hace falta saber cuando empezo.");
        this.estado = Objects.requireNonNull(estado, "Una partida tiene estado.");
        this.turnoActual = Objects.requireNonNull(turnoActual, "Una partida tiene un turno en curso.");
    }

    /**
     * Arranca la partida de una sala.
     *
     * <p>Solo el anfitrion puede hacerlo, y solo una vez: pedirselo a la sala
     * ({@link Sala#iniciarPartida}) antes de construir nada es lo que impide
     * que existan dos partidas para la misma sala.
     *
     * @param sala   sala ya marcada como en juego
     * @param ahora  reloj inyectado, para que las pruebas no dependan del sistema
     */
    public static Partida iniciar(Sala sala, Instant ahora) {
        Objects.requireNonNull(sala, "Sin sala no hay partida.");
        Objects.requireNonNull(ahora, "Hace falta el momento de inicio.");

        List<ParticipanteDePartida> enCombate = new ArrayList<>();
        // El anfitrion primero: es quien creo la sala y quien abre el combate.
        enCombate.add(deLaSala(sala, sala.idAnfitrion()));
        for (UUID jugador : sala.participantes()) {
            if (!jugador.equals(sala.idAnfitrion())) {
                enCombate.add(deLaSala(sala, jugador));
            }
        }
        if (sala.incluirHeroeIA()) {
            // La maquina combate con el heroe del anfitrion a vida completa.
            // Ver `ParticipanteDePartida.inteligenciaArtificial`: no se inventa
            // un heroe, se usa el unico que esta partida conoce, y de paso la
            // pelea queda pareja.
            FichaDeParticipante delAnfitrion = sala.fichaDe(sala.idAnfitrion());
            HeroeDeCombate heroeDeLaMaquina = delAnfitrion == null
                    ? null
                    : delAnfitrion.heroe().aPlenaVida();
            enCombate.add(ParticipanteDePartida.inteligenciaArtificial(
                    UUID.randomUUID(), heroeDeLaMaquina));
        }

        return new Partida(UUID.randomUUID(), sala.id(), enCombate,
                sala.recompensaCreditos(), ahora, EstadoPartida.EN_CURSO,
                Turno.primero(enCombate.get(0).idJugador()));
    }

    /**
     * Participante de combate a partir de lo que la sala guardo al dejarlo
     * entrar (SCRUM-1074).
     *
     * <p>El heroe NO se vuelve a pedir al inventario aqui: al arrancar solo esta
     * autenticado el anfitrion y este servicio no puede preguntar por el heroe
     * de otro. Ademas el heroe con el que alguien entro es el que apuesta.
     *
     * <p>Si la sala no tiene ficha —participante anterior a V7— el heroe queda
     * nulo, que es la verdad. Su barra no se pinta; inventarle una vida seria
     * peor.
     */
    private static ParticipanteDePartida deLaSala(Sala sala, UUID jugador) {
        FichaDeParticipante ficha = sala.fichaDe(jugador);
        return new ParticipanteDePartida(jugador, ficha == null ? null : ficha.heroe(),
                false, null, sala.recompensaCreditos());
    }

    /** Reconstruye una partida guardada. Solo para la capa de persistencia. */
    public static Partida rehidratar(UUID id, UUID idSala, EstadoPartida estado,
                                     List<ParticipanteDePartida> participantes, Turno turnoActual,
                                     int recompensaEnJuego, Instant iniciadaEn) {
        return new Partida(id, idSala, participantes, recompensaEnJuego, iniciadaEn,
                estado, turnoActual);
    }

    /**
     * Pasa el turno al siguiente participante, en el orden de la lista.
     *
     * <p>Rotacion simple y circular: es lo unico que este servicio puede
     * afirmar sin invadir al motor de combate. Quien decide que un turno
     * termino es el motor; esta clase solo sabe a quien le toca despues.
     *
     * @throws PartidaYaTerminada si el combate ya acabo
     */
    public void avanzarTurno() {
        if (estado == EstadoPartida.FINALIZADA) {
            throw new PartidaYaTerminada(id);
        }
        int actual = indiceDe(turnoActual.idJugador());
        int siguiente = (actual + 1) % participantes.size();
        turnoActual = turnoActual.siguiente(participantes.get(siguiente).idJugador());
    }

    /**
     * Aplica el dano que resolvio el motor y devuelve como queda el objetivo.
     *
     * <p>La vida vive aqui, no en el motor: el motor resuelve cuanto dano hace
     * un golpe y se olvida; quien lleva la cuenta es la partida, que es quien
     * la persiste.
     *
     * <p>Si el objetivo no tiene heroe conocido no se le puede restar nada y se
     * devuelve tal cual. No se inventa una vida para poder golpearle.
     *
     * @return el participante ya actualizado
     * @throws PartidaYaTerminada  si el combate acabo
     * @throws SinObjetivoPosible  si ese participante no esta en la partida
     */
    public ParticipanteDePartida aplicarDano(UUID idObjetivo, int dano) {
        if (estado == EstadoPartida.FINALIZADA) {
            throw new PartidaYaTerminada(id);
        }
        if (dano < 0) {
            throw new IllegalArgumentException("El dano no puede ser negativo.");
        }

        int posicion = indiceDe(idObjetivo, "El objetivo no esta en esta partida.");
        ParticipanteDePartida objetivo = participantes.get(posicion);

        if (objetivo.heroe() == null) {
            return objetivo;
        }

        ParticipanteDePartida golpeado = objetivo.conHeroe(
                objetivo.heroe().conVida(objetivo.heroe().vidaActual() - dano));
        participantes.set(posicion, golpeado);

        return golpeado;
    }

    /** Participantes que siguen en pie, en el orden de los turnos. */
    public List<ParticipanteDePartida> enPie() {
        return participantes.stream().filter(ParticipanteDePartida::enPie).toList();
    }

    /**
     * Termina el combate si ya solo queda uno en pie — HU-JUE-005.
     *
     * <p>Se comprueba <b>despues</b> de cada golpe y no antes: el que acaba de
     * caer todavia cuenta para el turno en el que cayo.
     *
     * @return true si este golpe acabo la partida
     */
    public boolean terminarSiSoloQuedaUno() {
        if (estado == EstadoPartida.FINALIZADA) {
            return false;
        }
        if (enPie().size() > 1) {
            return false;
        }
        terminar();
        return true;
    }

    /**
     * Quien gano, si la partida termino con alguien en pie.
     *
     * <p>Vacio cuando sigue en curso, y tambien cuando acabo sin nadie en pie
     * —dos caidas simultaneas—: eso es un empate, y declarar ganador a uno de
     * los dos seria inventarlo. El criterio de desempate es una decision del
     * Product Owner que todavia no esta tomada.
     */
    public java.util.Optional<ParticipanteDePartida> ganador() {
        if (estado != EstadoPartida.FINALIZADA) {
            return java.util.Optional.empty();
        }
        List<ParticipanteDePartida> vivos = enPie();
        return vivos.size() == 1 ? java.util.Optional.of(vivos.get(0)) : java.util.Optional.empty();
    }

    /**
     * Da el combate por terminado.
     *
     * <p>Idempotente: terminar dos veces una partida ya terminada no es un error
     * —el motor puede reintentar el aviso— y dejarla igual es la respuesta
     * correcta.
     */
    public void terminar() {
        estado = EstadoPartida.FINALIZADA;
    }

    private int indiceDe(UUID idJugador) {
        for (int i = 0; i < participantes.size(); i++) {
            if (participantes.get(i).idJugador().equals(idJugador)) {
                return i;
            }
        }
        // El turno siempre es de alguien que esta dentro; si no lo esta, el
        // estado guardado y los participantes no cuadran y hay que verlo.
        throw new IllegalStateException(
                "El turno de la partida " + id + " es de alguien que no esta en ella.");
    }

    /** Igual, pero para un objetivo elegido por el jugador: eso es un 409, no un fallo. */
    private int indiceDe(UUID idJugador, String siNoEsta) {
        for (int i = 0; i < participantes.size(); i++) {
            if (participantes.get(i).idJugador().equals(idJugador)) {
                return i;
            }
        }
        throw new SinObjetivoPosible(siNoEsta);
    }

    public UUID id() {
        return id;
    }

    public UUID idSala() {
        return idSala;
    }

    public EstadoPartida estado() {
        return estado;
    }

    /** Participantes en el orden de los turnos. Copia: nadie los altera desde fuera. */
    public List<ParticipanteDePartida> participantes() {
        return Collections.unmodifiableList(participantes);
    }

    public Turno turnoActual() {
        return turnoActual;
    }

    public int recompensaEnJuego() {
        return recompensaEnJuego;
    }

    public Instant iniciadaEn() {
        return iniciadaEn;
    }
}
