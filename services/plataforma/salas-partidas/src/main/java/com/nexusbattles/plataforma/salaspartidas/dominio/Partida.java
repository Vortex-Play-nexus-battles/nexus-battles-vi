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
 * efectos. Eso es del motor de combate, que resuelve cada accion y devuelve
 * el estado de todos; aqui se guarda ese estado ({@link #aplicarCombate}) y
 * vive el ciclo de vida —iniciar, avanzar el turno, terminar— con lo que la
 * vista de combate necesita pintar y hace falta para reconectar.
 *
 * <p><b>El orden de los participantes es el orden de los turnos</b> y desde B7
 * se SORTEA al empezar entre todos (§6.1.3, {@link OrdenDeTurnos}) con una
 * semilla que la partida guarda. No cambia hasta el final.
 *
 * <p><b>Bloqueo optimista (B7).</b> {@link #version()} viaja de la lectura a
 * la escritura: dos acciones del mismo turno no se aplican las dos.
 */
public class Partida {

    private final UUID id;
    private final UUID idSala;
    private final List<ParticipanteDePartida> participantes;
    private final int recompensaEnJuego;
    private final Instant iniciadaEn;
    private final long version;
    private final Long semillaDelOrden;

    private EstadoPartida estado;
    private Turno turnoActual;
    private Instant finalizadaEn;
    private Instant turnoVenceEn;

    private Partida(UUID id, UUID idSala, List<ParticipanteDePartida> participantes,
                    int recompensaEnJuego, Instant iniciadaEn, EstadoPartida estado,
                    Turno turnoActual) {
        this(id, idSala, participantes, recompensaEnJuego, iniciadaEn, estado, turnoActual, 0L, null, null,
                null);
    }

    private Partida(UUID id, UUID idSala, List<ParticipanteDePartida> participantes,
                    int recompensaEnJuego, Instant iniciadaEn, EstadoPartida estado,
                    Turno turnoActual, long version, Long semillaDelOrden, Instant finalizadaEn,
                    Instant turnoVenceEn) {
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
        this.version = version;
        this.semillaDelOrden = semillaDelOrden;
        this.finalizadaEn = finalizadaEn;
        this.turnoVenceEn = turnoVenceEn;
    }

    /**
     * Arranca la partida de una sala en el orden de entrada, con la maquina
     * combatiendo con un rival del mismo prototipo y nivel que el heroe del
     * anfitrion ({@link HeroeDeCombate#comoRivalDeLaMaquina()}).
     *
     * <p>Ninguna partida real se inicia asi: {@code IniciarPartida} sortea el
     * orden (§6.1.3) y le da a la maquina un heroe aleatorio del catalogo
     * (D-B7-11) con {@link #iniciar(Sala, Instant, OrdenDeTurnos, List)}. Este
     * atajo existe para las pruebas de otras reglas, que necesitan saber quien
     * juega cada turno.
     *
     * @param sala   sala ya marcada como en juego
     * @param ahora  reloj inyectado, para que las pruebas no dependan del sistema
     */
    public static Partida iniciar(Sala sala, Instant ahora) {
        return iniciar(sala, ahora, OrdenDeTurnos.DE_ENTRADA, List.of());
    }

    /**
     * Arranca la partida de una sala.
     *
     * <p>Solo el anfitrion puede hacerlo, y solo una vez: pedirselo a la sala
     * ({@link Sala#iniciarPartida}) antes de construir nada es lo que impide
     * que existan dos partidas para la misma sala.
     *
     * <p>Los equipos del modo cooperativo se reparten en el ORDEN DE ENTRADA
     * (ver {@link #repartirEnEquipos}) y despues se sortea el orden de los
     * turnos entre todos: el equipo no depende del sorteo.
     *
     * @param sala              sala ya marcada como en juego
     * @param ahora             reloj inyectado, para que las pruebas no dependan del sistema
     * @param orden             como se ordenan los turnos (sorteo con semilla, §6.1.3)
     * @param heroesDeLaMaquina un heroe por cupo de la IA (D-B7-11); los que
     *                          falten combaten con un rival del prototipo y el
     *                          nivel del heroe del anfitrion, sin su nombre ni
     *                          su equipo
     */
    public static Partida iniciar(Sala sala, Instant ahora, OrdenDeTurnos orden,
                                  List<HeroeDeCombate> heroesDeLaMaquina) {
        Objects.requireNonNull(sala, "Sin sala no hay partida.");
        Objects.requireNonNull(ahora, "Hace falta el momento de inicio.");
        Objects.requireNonNull(orden, "Hace falta como se ordenan los turnos.");
        List<HeroeDeCombate> heroesIA = heroesDeLaMaquina == null ? List.of() : heroesDeLaMaquina;

        List<ParticipanteDePartida> enCombate = new ArrayList<>();
        // En orden de entrada: el anfitrion primero, porque abre el equipo 1.
        enCombate.add(deLaSala(sala, sala.idAnfitrion()));
        for (UUID jugador : sala.participantes()) {
            if (!jugador.equals(sala.idAnfitrion())) {
                enCombate.add(deLaSala(sala, jugador));
            }
        }
        // Un participante por cada cupo de la maquina (HU-SAL-004), detras de
        // las personas.
        FichaDeParticipante delAnfitrion = sala.fichaDe(sala.idAnfitrion());
        for (int i = 0; i < sala.heroesIA(); i++) {
            HeroeDeCombate heroeDeLaMaquina;
            if (i < heroesIA.size() && heroesIA.get(i) != null) {
                heroeDeLaMaquina = heroesIA.get(i);
            } else {
                // Sin catalogo: un rival del mismo prototipo y nivel, NO una
                // copia del heroe del anfitrion con su nombre y su equipo.
                heroeDeLaMaquina = delAnfitrion == null ? null : delAnfitrion.heroe().comoRivalDeLaMaquina();
            }
            enCombate.add(ParticipanteDePartida.inteligenciaArtificial(
                    UUID.randomUUID(), heroeDeLaMaquina));
        }

        List<ParticipanteDePartida> enTurnos =
                orden.aplicar(repartirEnEquipos(enCombate, sala.tamanoEquipo()));
        return new Partida(UUID.randomUUID(), sala.id(), enTurnos,
                sala.recompensaCreditos(), ahora, EstadoPartida.EN_CURSO,
                Turno.primero(enTurnos.get(0).idJugador()), 0L, orden.semilla(), null, null);
    }

    /**
     * Equipos del modo cooperativo — RF-JUE-004, HU-SAL-004.
     *
     * <p>Se llenan en el orden de la lista, que es el orden de entrada: el
     * anfitrion abre el equipo 1, y cada equipo se completa antes de abrir el
     * siguiente. Es la regla mas simple que respeta el unico dato que fija el
     * requisito (el tamano maximo); no se baraja ni se equilibra porque ninguna
     * HU lo pide. Sin tamano de equipo, nadie tiene equipo: todos contra todos.
     */
    private static List<ParticipanteDePartida> repartirEnEquipos(List<ParticipanteDePartida> enCombate,
                                                                 Integer tamanoEquipo) {
        if (tamanoEquipo == null || tamanoEquipo < 1) {
            return enCombate;
        }
        List<ParticipanteDePartida> conEquipo = new ArrayList<>(enCombate.size());
        for (int i = 0; i < enCombate.size(); i++) {
            conEquipo.add(enCombate.get(i).conEquipo(i / tamanoEquipo + 1));
        }
        return conEquipo;
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

    /** Reconstruye una partida guardada, sin los campos de B7. Para dobles y fixtures. */
    public static Partida rehidratar(UUID id, UUID idSala, EstadoPartida estado,
                                     List<ParticipanteDePartida> participantes, Turno turnoActual,
                                     int recompensaEnJuego, Instant iniciadaEn) {
        return new Partida(id, idSala, participantes, recompensaEnJuego, iniciadaEn,
                estado, turnoActual);
    }

    /**
     * Reconstruye una partida guardada con todo su estado. Solo para la capa de
     * persistencia.
     *
     * @param version         marca de concurrencia tal como se leyo
     * @param semillaDelOrden semilla del sorteo de turnos, o nula (orden de entrada)
     * @param finalizadaEn    cuando termino, o nula
     * @param turnoVenceEn    cuando se agota el turno en curso, o nula sin limite
     */
    public static Partida rehidratar(UUID id, UUID idSala, EstadoPartida estado,
                                     List<ParticipanteDePartida> participantes, Turno turnoActual,
                                     int recompensaEnJuego, Instant iniciadaEn, long version,
                                     Long semillaDelOrden, Instant finalizadaEn, Instant turnoVenceEn) {
        return new Partida(id, idSala, participantes, recompensaEnJuego, iniciadaEn,
                estado, turnoActual, version, semillaDelOrden, finalizadaEn, turnoVenceEn);
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
        // El vencimiento era del turno que termina; el siguiente lo fija quien
        // conoce el tiempo por turno (D-B7-14).
        turnoVenceEn = null;
        // Quien ya cayo no juega (HU-SAL-004, SCRUM-1079): en una partida de
        // seis el turno saltaria a heroes derrotados y la vista esperaria a
        // alguien que no puede actuar. Se busca al siguiente en pie; si no
        // hubiera ninguno —no deberia pasar con la partida en curso— se rota
        // igual, para no quedarse quieto.
        for (int paso = 1; paso <= participantes.size(); paso++) {
            ParticipanteDePartida candidato = participantes.get((actual + paso) % participantes.size());
            if (candidato.enPie()) {
                turnoActual = turnoActual.siguiente(candidato.idJugador());
                return;
            }
        }
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

    /**
     * Guarda lo que resolvio el motor para un participante (B7): su vida, su
     * vida maxima en su nivel y su estado de combate.
     *
     * <p>El motor es quien decide los numeros; aqui solo se guardan. A un
     * participante sin heroe conocido no se le inventa uno: se guarda solo su
     * estado de combate.
     *
     * @throws PartidaYaTerminada si el combate acabo
     * @throws SinObjetivoPosible si ese participante no esta en la partida
     */
    public ParticipanteDePartida aplicarCombate(UUID idParticipante, int vidaActual, int vidaMaxima,
                                                EstadoDeCombate combate) {
        if (estado == EstadoPartida.FINALIZADA) {
            throw new PartidaYaTerminada(id);
        }
        int posicion = indiceDe(idParticipante, "Ese participante no esta en esta partida.");
        ParticipanteDePartida actual = participantes.get(posicion);
        ParticipanteDePartida nuevo = actual.heroe() == null
                ? actual.conCombate(combate)
                : actual.conHeroe(actual.heroe().conVida(vidaActual, vidaMaxima)).conCombate(combate);
        participantes.set(posicion, nuevo);
        return nuevo;
    }

    /**
     * Se rinde — revision del modo jugador del 6-oct (salas-partidas.yaml 1.10.0,
     * {@code POST /partidas/{id}/rendicion}).
     *
     * <p>Rendirse es dejar el combate: el heroe de quien se rinde queda fuera de
     * combate, igual que si hubiera caido, y a partir de ahi manda la regla de
     * siempre ({@link #terminarSiSoloQuedaUno}): si su bando se queda sin nadie
     * en pie, gana el otro. El ganador lo decide la partida, nunca quien llama.
     * No se inventa un golpe: no hay dano ni ejecutor, solo la retirada.
     *
     * <p>Idempotente: rendirse dos veces, o con el heroe ya caido, no cambia
     * nada.
     *
     * @return quien se rindio, ya fuera de combate
     * @throws PartidaYaTerminada  si el combate ya acabo
     * @throws SinObjetivoPosible  si no juega esta partida
     * @throws AccionNoPermitida   si es la maquina, o si no se conoce su heroe
     */
    public ParticipanteDePartida rendir(UUID idJugador) {
        if (estado == EstadoPartida.FINALIZADA) {
            throw new PartidaYaTerminada(id);
        }
        int posicion = indiceDe(idJugador, "Ese jugador no esta en esta partida.");
        ParticipanteDePartida quien = participantes.get(posicion);
        if (quien.esIA()) {
            throw new AccionNoPermitida("RENDICION_NO_PERMITIDA", "La IA no se rinde.");
        }
        if (quien.heroe() == null) {
            throw new AccionNoPermitida("RENDICION_NO_PERMITIDA",
                    "Sin héroe conocido no se le puede dar por fuera de combate.");
        }
        if (!quien.enPie()) {
            return quien;
        }
        ParticipanteDePartida rendido = quien.conHeroe(quien.heroe().conVida(0));
        participantes.set(posicion, rendido);
        return rendido;
    }

    /**
     * Guarda el estado de todos los combatientes de una respuesta del motor.
     * Uno que no este en la partida se ignora: el motor solo devuelve a quien
     * se le mando.
     */
    public void aplicar(List<CombatienteResuelto> combatientes) {
        for (CombatienteResuelto c : combatientes) {
            if (participante(c.id()).isPresent()) {
                aplicarCombate(c.id(), c.vidaActual(), c.vidaMaxima(), c.estado());
            }
        }
    }

    /** El participante, si esta en la partida. */
    public java.util.Optional<ParticipanteDePartida> participante(UUID idJugador) {
        return participantes.stream().filter(p -> p.idJugador().equals(idJugador)).findFirst();
    }

    /** Si ese jugador combate en esta partida (la maquina no es un jugador que pregunte). */
    public boolean esParticipante(UUID idJugador) {
        return idJugador != null && participante(idJugador).isPresent();
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
        return terminarSiSoloQuedaUno(null);
    }

    /** Igual, anotando cuando termino (B7). */
    public boolean terminarSiSoloQuedaUno(Instant ahora) {
        if (estado == EstadoPartida.FINALIZADA) {
            return false;
        }
        if (bandosEnPie() > 1) {
            return false;
        }
        terminar(ahora);
        return true;
    }

    /**
     * El final formal (1.7.0): {@link ResultadoDePartida#GANADOR} cuando queda
     * en pie un solo heroe o un solo equipo, {@link ResultadoDePartida#EMPATE}
     * cuando no queda nadie. Vacio mientras sigue en curso.
     */
    public java.util.Optional<ResultadoDePartida> resultado() {
        if (estado != EstadoPartida.FINALIZADA) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(bandosEnPie() == 1 ? ResultadoDePartida.GANADOR : ResultadoDePartida.EMPATE);
    }

    /**
     * Cuantos bandos siguen en pie. En el modo cooperativo un bando es un
     * equipo (HU-SAL-004): el combate no acaba mientras queden dos equipos
     * con alguien en pie. Sin equipos, cada participante es su propio bando.
     */
    private long bandosEnPie() {
        List<ParticipanteDePartida> vivos = enPie();
        if (!conEquipos()) {
            return vivos.size();
        }
        return vivos.stream().map(ParticipanteDePartida::equipo).distinct().count();
    }

    /** Si la partida se juega por equipos (modo cooperativo de RF-JUE-004). */
    public boolean conEquipos() {
        return participantes.stream().anyMatch(p -> p.equipo() != null);
    }

    /** Si dos participantes comparten equipo. Sin equipos, nadie es companero de nadie. */
    public boolean sonDelMismoEquipo(UUID uno, UUID otro) {
        Integer equipoDeUno = equipoDe(uno);
        return equipoDeUno != null && equipoDeUno.equals(equipoDe(otro));
    }

    private Integer equipoDe(UUID idJugador) {
        // Sin map(): Optional no admite un equipo nulo, que es lo normal fuera
        // del modo cooperativo.
        for (ParticipanteDePartida p : participantes) {
            if (p.idJugador().equals(idJugador)) {
                return p.equipo();
            }
        }
        return null;
    }

    /**
     * Quien gano, si la partida termino con <b>una sola persona</b> en pie.
     *
     * <p>Vacio cuando sigue en curso, y tambien cuando acabo sin nadie en pie
     * —dos caidas simultaneas—: eso es un empate, y declarar ganador a uno de
     * los dos seria inventarlo. El criterio de desempate es una decision del
     * Product Owner que todavia no esta tomada. Vacio tambien en el modo
     * cooperativo, siempre: ahi gana un equipo ({@link #equipoGanador()},
     * {@link #ganadores()}), no una persona, y como se reparte la apuesta
     * entre companeros —o si el companero caido pierde la suya— tampoco esta
     * decidido (D-12). Declarar ganador al unico en pie le daria lo apostado
     * por su propio companero.
     */
    public java.util.Optional<ParticipanteDePartida> ganador() {
        if (estado != EstadoPartida.FINALIZADA || conEquipos()) {
            return java.util.Optional.empty();
        }
        List<ParticipanteDePartida> vivos = enPie();
        return vivos.size() == 1 ? java.util.Optional.of(vivos.get(0)) : java.util.Optional.empty();
    }

    /**
     * Quien gano: el heroe que quedo en pie o, en el modo cooperativo, TODOS
     * los integrantes del equipo ganador, tambien los que cayeron — gana el
     * equipo (D-B7-15, salas-partidas.yaml 1.7.0). Vacio si sigue en curso o en
     * empate.
     */
    public List<ParticipanteDePartida> ganadores() {
        if (resultado().filter(r -> r == ResultadoDePartida.GANADOR).isEmpty()) {
            return List.of();
        }
        if (!conEquipos()) {
            return enPie();
        }
        Integer equipo = equipoGanador().orElse(null);
        return participantes.stream().filter(p -> Objects.equals(p.equipo(), equipo)).toList();
    }

    /** El equipo que gano, si la partida era por equipos y termino con alguien en pie. */
    public java.util.Optional<Integer> equipoGanador() {
        if (estado != EstadoPartida.FINALIZADA || !conEquipos()) {
            return java.util.Optional.empty();
        }
        List<Integer> equiposEnPie = enPie().stream()
                .map(ParticipanteDePartida::equipo)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        // Exactamente uno: con ninguno es empate, y con dos la partida no
        // deberia haber terminado.
        return equiposEnPie.size() == 1
                ? java.util.Optional.of(equiposEnPie.get(0))
                : java.util.Optional.empty();
    }

    /**
     * Da el combate por terminado.
     *
     * <p>Idempotente: terminar dos veces una partida ya terminada no es un error
     * —el motor puede reintentar el aviso— y dejarla igual es la respuesta
     * correcta.
     */
    public void terminar() {
        terminar(null);
    }

    /** Igual, anotando cuando termino. La primera hora que se anota es la que vale. */
    public void terminar(Instant ahora) {
        estado = EstadoPartida.FINALIZADA;
        turnoVenceEn = null;
        if (finalizadaEn == null) {
            finalizadaEn = ahora;
        }
    }

    /**
     * Cuando se agota el turno en curso, o {@code null} sin limite. Lo fija el
     * caso de uso con el parametro {@code salas.partidas.segundos-por-turno},
     * que nace sin valor: el documento no fija un tiempo por turno (D-B7-14).
     */
    public void fijarVencimientoDelTurno(Instant venceEn) {
        this.turnoVenceEn = estado == EstadoPartida.FINALIZADA ? null : venceEn;
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

    /** Marca de concurrencia tal como se leyo (bloqueo optimista, B7). */
    public long version() {
        return version;
    }

    /** Semilla del sorteo del orden de turnos (§6.1.3), o nula si se jugo en orden de entrada. */
    public Long semillaDelOrden() {
        return semillaDelOrden;
    }

    /** Cuando termino, o nula en curso. */
    public Instant finalizadaEn() {
        return finalizadaEn;
    }

    /** Cuando se agota el turno en curso, o nula sin limite (D-B7-14). */
    public Instant turnoVenceEn() {
        return turnoVenceEn;
    }
}
