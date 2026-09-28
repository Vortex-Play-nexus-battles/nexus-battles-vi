package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import com.nexusbattles.plataforma.salaspartidas.dominio.AccionNoPermitida;
import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta;
import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.EventoDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.InicioDeTurno;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.NoEsTuTurno;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaYaTerminada;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResolucionDeAccion;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.SinObjetivoPosible;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * El jugador juega su turno y el combate avanza — RF-JUE-006, RF-JUE-017, §6.
 *
 * <p>Desde B7 el combate es el de la seccion 6 del documento, con el heroe real:
 * este servicio no decide ninguna regla de juego, se las pregunta al motor de
 * combate ({@link MotorDeCombate}) y guarda lo que responde. El orden es:
 *
 * <ol>
 *   <li>Comprobar que puede jugar: partida viva y turno suyo. Quien juega sale
 *       del token (lo pone el canal), nunca del mensaje.</li>
 *   <li>Pedir al motor la accion ({@code POST /combate/acciones}). El motor
 *       valida lo que el cliente no puede decidir —que la accion sea de su
 *       heroe y este desbloqueada, que no este en carga, el objetivo, que un
 *       sanador no ataque— y un rechazo es {@link AccionNoPermitida}: no se
 *       aplica nada y el turno sigue siendo suyo.</li>
 *   <li>Guardar el estado de todos que devolvio el motor (vida, poder,
 *       cargas, efectos).</li>
 *   <li>Terminar si queda un solo heroe o un solo equipo en pie, o pasar el
 *       turno al siguiente en pie y pedir al motor lo que pasa al EMPEZAR su
 *       turno ({@code POST /combate/turnos}: +2 de poder, sangrados,
 *       protecciones que terminan). Un sangrado puede tumbarlo y entonces el
 *       turno sigue pasando.</li>
 *   <li>Guardar la partida — con bloqueo optimista: si otra accion del mismo
 *       turno ya entro, esta recibe {@link PartidaModificadaConcurrentemente}
 *       y no se aplica — y DESPUES anunciar: la accion resuelta, los efectos
 *       por turno y el cambio de turno, o el fin.</li>
 *   <li>Si el turno es de la maquina, jugarlo con las mismas reglas
 *       ({@link MotorDeCombate#DECISION_DE_LA_MAQUINA}).</li>
 * </ol>
 *
 * <p><b>Guardar antes de anunciar</b>, como en el resto del servicio: al reves
 * se anunciaria una vida que todavia podria perderse.
 */
public class EjecutarAccion {

    private static final Logger BITACORA = LoggerFactory.getLogger(EjecutarAccion.class);

    /** Motivos del cambio de turno (canal 1.5.0). */
    static final String POR_ACCION = "ACCION";
    static final String POR_TIEMPO_AGOTADO = "TIEMPO_AGOTADO";
    static final String POR_TURNO_PERDIDO = "TURNO_PERDIDO";

    /** Codigo por defecto cuando el cliente no manda uno. */
    static final String ACCION_BASICA = MotorDeCombate.ATAQUE_BASICO;

    /** D-B7-14: la clave del tiempo por turno en el catalogo de admin-parametros. */
    public static final String CLAVE_SEGUNDOS_POR_TURNO = "salas.partidas.segundos-por-turno";

    private final RepositorioDePartidas partidas;
    private final RepositorioDeSalas salas;
    private final CanalDePartida canal;
    private final MotorDeCombate motor;
    private final LiquidarApuesta apuesta;
    private final AcreditarRecompensa recompensa;
    private final InformarEncuentroDeTorneo torneo;
    private final Clock reloj;
    private final Supplier<Integer> segundosPorTurno;

    public EjecutarAccion(RepositorioDePartidas partidas, CanalDePartida canal,
                          MotorDeCombate motor, LiquidarApuesta apuesta, AcreditarRecompensa recompensa) {
        this(partidas, canal, motor, apuesta, recompensa, null);
    }

    /**
     * @param torneo informa el ganador a torneos cuando la sala es un encuentro
     *               (HU-TOR-004, CA-04); nulo en los dobles que no lo miran
     */
    public EjecutarAccion(RepositorioDePartidas partidas, CanalDePartida canal,
                          MotorDeCombate motor, LiquidarApuesta apuesta, AcreditarRecompensa recompensa,
                          InformarEncuentroDeTorneo torneo) {
        this(partidas, null, canal, motor, apuesta, recompensa, torneo, Clock.systemUTC(), () -> null);
    }

    /**
     * @param salas            para dar la sala por terminada al acabar la partida
     *                         (1.7.0); nulo en los dobles que no lo miran
     * @param reloj            reloj del servicio
     * @param segundosPorTurno tiempo por turno ({@code salas.partidas.segundos-por-turno});
     *                         nulo = sin limite, que es como nace (D-B7-14)
     */
    public EjecutarAccion(RepositorioDePartidas partidas, RepositorioDeSalas salas, CanalDePartida canal,
                          MotorDeCombate motor, LiquidarApuesta apuesta, AcreditarRecompensa recompensa,
                          InformarEncuentroDeTorneo torneo, Clock reloj, Supplier<Integer> segundosPorTurno) {
        this.partidas = Objects.requireNonNull(partidas);
        this.salas = salas;
        this.canal = Objects.requireNonNull(canal);
        this.motor = Objects.requireNonNull(motor, "Sin motor no hay combate.");
        this.apuesta = Objects.requireNonNull(apuesta, "Sin liquidacion la apuesta se perderia.");
        this.recompensa = Objects.requireNonNull(recompensa, "Sin recompensa jugar no daria creditos.");
        this.torneo = torneo;
        this.reloj = Objects.requireNonNull(reloj);
        this.segundosPorTurno = Objects.requireNonNull(segundosPorTurno);
    }

    /**
     * @param idPartida  partida en la que se juega
     * @param idJugador  jugador autenticado que manda la accion
     * @param idObjetivo a quien apunta; opcional si solo hay un objetivo posible
     * @param codigo     accion elegida; nulo cae en la basica de su heroe
     * @return la partida despues de la accion (y de los turnos de la maquina)
     * @throws PartidaNoEncontrada                si no existe
     * @throws PartidaYaTerminada                 si el combate ya acabo
     * @throws NoEsTuTurno                        si no le toca a quien envia
     * @throws AccionNoPermitida                  si el motor rechaza la accion
     * @throws PartidaModificadaConcurrentemente  si otra accion del turno entro antes
     */
    public Partida ejecutar(UUID idPartida, UUID idJugador, UUID idObjetivo, String codigo) {
        Objects.requireNonNull(idPartida, "Hace falta la partida en la que se juega.");
        Objects.requireNonNull(idJugador, "Hace falta quien juega el turno.");

        Partida partida = partidas.buscarPorId(idPartida)
                .orElseThrow(() -> new PartidaNoEncontrada(idPartida));

        // El orden importa: una partida terminada se rechaza como terminada, no
        // como turno ajeno. Al ultimo en jugar, un «no es tu turno» no le
        // explicaria nada, porque su turno si era.
        if (partida.estado() == EstadoPartida.FINALIZADA) {
            throw new PartidaYaTerminada(idPartida);
        }
        if (!partida.turnoActual().idJugador().equals(idJugador)) {
            throw new NoEsTuTurno();
        }

        ParticipanteDePartida ejecutor = participante(partida, idJugador);
        if (!combateConReglas(ejecutor)) {
            // Sin heroe o sin prototipo no hay nada que mandar al motor: pasa
            // con los participantes anteriores a la puerta de SCRUM-1074. Se
            // pasa turno sin golpear en vez de inventar un ataque.
            return jugarTurnosDeLaMaquina(pasarTurnoSinAccion(partida, POR_TURNO_PERDIDO));
        }
        if (idObjetivo != null && partida.participante(idObjetivo).isEmpty()) {
            throw new SinObjetivoPosible("Ese objetivo no esta en la partida.");
        }

        String pedida = codigo == null || codigo.isBlank() ? null : codigo.trim();
        ResolucionDeAccion resolucion = motor.resolverAccion(
                pedida == null ? basicaDe(ejecutor) : pedida, idJugador, idObjetivo, partida);

        return jugarTurnosDeLaMaquina(aplicarYAnunciar(partida, resolucion));
    }

    /**
     * El turno en curso se agoto sin accion (D-B7-14): pasa al siguiente, con
     * motivo {@code TIEMPO_AGOTADO}. No hace nada si la partida ya no esta en
     * ese caso —termino, o alguien jugo mientras tanto—.
     *
     * @return la partida despues, o vacio si no habia nada que agotar
     */
    public java.util.Optional<Partida> agotarTurno(UUID idPartida) {
        Partida partida = partidas.buscarPorId(idPartida).orElse(null);
        Instant ahora = reloj.instant();
        if (partida == null || partida.estado() == EstadoPartida.FINALIZADA || partida.turnoVenceEn() == null
                || partida.turnoVenceEn().isAfter(ahora)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(jugarTurnosDeLaMaquina(pasarTurnoSinAccion(partida, POR_TIEMPO_AGOTADO)));
    }

    /**
     * Juega los turnos de la maquina si el turno en curso es suyo. Lo llama
     * {@code IniciarPartida} al empezar: con el orden sorteado (§6.1.3) la
     * maquina puede abrir el combate, y nadie mas jugaria por ella.
     */
    public void jugarLaMaquinaSiLeToca(UUID idPartida) {
        partidas.buscarPorId(idPartida)
                .filter(partida -> partida.estado() == EstadoPartida.EN_CURSO)
                .ifPresent(this::jugarTurnosDeLaMaquina);
    }

    /**
     * Aplica lo que resolvio el motor, cierra o pasa el turno, guarda y anuncia.
     */
    private Partida aplicarYAnunciar(Partida partida, ResolucionDeAccion resolucion) {
        Instant ahora = reloj.instant();
        partida.aplicar(resolucion.combatientes());

        List<AccionResuelta> anuncios = new ArrayList<>();
        anuncios.add(anuncioDe(partida, resolucion));

        boolean termino = partida.terminarSiSoloQuedaUno(ahora);
        if (!termino) {
            termino = pasarTurno(partida, anuncios, ahora);
        }
        Partida guardada = partidas.guardar(partida);

        anuncios.forEach(canal::anunciarAccionResuelta);
        // Despues de la accion, y en este orden: quien mira la vista ve primero
        // la barra moverse y luego el resultado. Al reves habria que animar
        // hacia atras.
        if (termino) {
            terminar(guardada);
        } else {
            canal.anunciarTurno(guardada, POR_ACCION);
        }
        return guardada;
    }

    /** Pasa el turno sin que nadie haya jugado: guarda y anuncia. */
    private Partida pasarTurnoSinAccion(Partida partida, String motivo) {
        List<AccionResuelta> anuncios = new ArrayList<>();
        boolean termino = pasarTurno(partida, anuncios, reloj.instant());
        Partida guardada = partidas.guardar(partida);
        anuncios.forEach(canal::anunciarAccionResuelta);
        if (termino) {
            terminar(guardada);
        } else {
            canal.anunciarTurno(guardada, motivo);
        }
        return guardada;
    }

    /**
     * Pasa el turno al siguiente en pie y le pide al motor lo que ocurre al
     * empezar su turno. Si un efecto por turno lo tumba, el turno sigue
     * pasando; si deja un solo bando en pie, la partida termina.
     *
     * <p>Si el motor no responde al empezar un turno, el turno empieza igual
     * sin recuperar poder ni aplicar efectos, y queda en la bitacora: la accion
     * de quien jugo ya se resolvio y no se le puede devolver un error por algo
     * que paso despues.
     *
     * @return true si la partida termino
     */
    private boolean pasarTurno(Partida partida, List<AccionResuelta> anuncios, Instant ahora) {
        for (int guarda = partida.participantes().size(); guarda > 0; guarda--) {
            partida.avanzarTurno();
            UUID enTurno = partida.turnoActual().idJugador();
            if (combateConReglas(participante(partida, enTurno))) {
                try {
                    InicioDeTurno inicio = motor.iniciarTurno(enTurno, partida, false);
                    partida.aplicar(inicio.combatientes());
                    anuncios.addAll(efectosPorTurno(partida, inicio));
                } catch (MotorNoDisponible | DependenciaDegradada noResponde) {
                    BITACORA.warn("La partida {}: el motor no respondio al empezar el turno {}; "
                                    + "el turno empieza sin recuperar poder ni aplicar efectos: {}",
                            partida.id(), partida.turnoActual().numeroTurno(), noResponde.getMessage());
                }
                if (partida.terminarSiSoloQuedaUno(ahora)) {
                    return true;
                }
            }
            if (participante(partida, enTurno).enPie()) {
                fijarVencimiento(partida, ahora);
                return false;
            }
        }
        fijarVencimiento(partida, ahora);
        return false;
    }

    private void fijarVencimiento(Partida partida, Instant ahora) {
        Integer segundos = segundosPorTurno.get();
        partida.fijarVencimientoDelTurno(segundos == null || segundos <= 0 ? null : ahora.plusSeconds(segundos));
    }

    /**
     * La maquina juega sus turnos — HU-SAL-004, §6.1.3.
     *
     * <p>Con las mismas reglas que un humano: su accion pasa por el mismo motor
     * con {@link MotorDeCombate#DECISION_DE_LA_MAQUINA}, que elige con una
     * politica simple y determinista (D-B7-12). Encadena mientras el turno sea
     * de una maquina; la guarda del bucle impide dar vueltas si el turno no
     * avanza.
     */
    private Partida jugarTurnosDeLaMaquina(Partida partida) {
        Partida actual = partida;
        int guarda = actual.participantes().size() * 2;
        while (guarda-- > 0 && actual.estado() != EstadoPartida.FINALIZADA) {
            ParticipanteDePartida enTurno = participante(actual, actual.turnoActual().idJugador());
            if (!enTurno.esIA()) {
                return actual;
            }
            try {
                actual = jugarTurnoDeLaMaquina(actual, enTurno);
            } catch (PartidaModificadaConcurrentemente otroSeAdelanto) {
                // Otra escritura (el vencimiento de un turno) ya movio la
                // partida: quien la movio anuncia lo suyo.
                return actual;
            }
        }
        return actual;
    }

    /**
     * Un turno de la maquina. Si el motor no contesta o rechaza su decision, la
     * maquina PASA el turno en vez de propagar el error: quien mando la accion
     * anterior ya la vio resuelta, y devolverle un error por algo que ocurrio
     * despues seria mentirle sobre su propia jugada.
     */
    private Partida jugarTurnoDeLaMaquina(Partida partida, ParticipanteDePartida maquina) {
        if (!combateConReglas(maquina)) {
            return pasarTurnoSinAccion(partida, POR_TURNO_PERDIDO);
        }
        ResolucionDeAccion resolucion;
        try {
            resolucion = motor.resolverAccion(MotorDeCombate.DECISION_DE_LA_MAQUINA, maquina.idJugador(), null,
                    partida);
        } catch (AccionNoPermitida | MotorNoDisponible | DependenciaDegradada noSePudo) {
            BITACORA.warn("La maquina {} de la partida {} pasa el turno: {}", maquina.idJugador(), partida.id(),
                    noSePudo.getMessage());
            return pasarTurnoSinAccion(partida, POR_TURNO_PERDIDO);
        }
        return aplicarYAnunciar(partida, resolucion);
    }

    /**
     * La partida termino: la sala queda FINALIZADA (1.7.0), se liquida la
     * apuesta (HU-JUE-014, CA-04), se informa el resultado al libro para la
     * recompensa por jugar (HU-JUE-012) y se anuncia el resultado.
     *
     * <p>Los dos movimientos van ANTES del aviso para que viajen en el mismo
     * mensaje, y en este orden (HU-JUE-012, CA-03): primero la apuesta, luego
     * la recompensa. Si el libro de creditos no responde, cada uno queda
     * anotado como pendiente por su lado y devuelve vacio: el aviso sale igual
     * y el reintento lo completara despues.
     */
    private void terminar(Partida terminada) {
        darPorTerminadaLaSala(terminada);
        List<RepartoDeCreditos> reparto = apuesta.alTerminar(terminada);
        List<CreditoPorPartida> premio = recompensa.alTerminar(terminada);
        canal.anunciarFin(terminada, reparto, premio);
        // Despues del aviso: el resultado del encuentro es cosa de torneos y un
        // fallo ahi no puede retrasar lo que ven los jugadores. Nunca lanza.
        if (torneo != null) {
            try {
                torneo.alTerminar(terminada);
            } catch (RuntimeException fallo) {
                BITACORA.warn("La partida {} termino pero no se pudo anotar el encuentro de torneo: {}",
                        terminada.id(), fallo.getMessage());
            }
        }
    }

    /** La sala pasa a FINALIZADA. Si no se puede ahora, queda en la bitacora: la partida es la verdad. */
    private void darPorTerminadaLaSala(Partida terminada) {
        if (salas == null) {
            return;
        }
        try {
            salas.buscarPorId(terminada.idSala()).ifPresent(sala -> {
                if (sala.terminarPartida()) {
                    salas.guardar(sala);
                }
            });
        } catch (SalaModificadaConcurrentemente | IllegalStateException otraEscritura) {
            BITACORA.warn("La partida {} termino pero la sala {} no se pudo marcar como finalizada: {}",
                    terminada.id(), terminada.idSala(), otraEscritura.getMessage());
        }
    }

    // ------------------------------------------------------------------ anuncios

    /** El aviso de la accion: lo que se jugo, la tirada y como quedo cada tocado. */
    private static AccionResuelta anuncioDe(Partida partida, ResolucionDeAccion r) {
        ResolucionDeAccion.Golpe golpe = r.ataque();
        String nombre = golpe != null && golpe.categoria() != null ? golpe.categoria() : r.accionEjecutada();
        String pedida = r.accion() != null && !r.accion().equals(r.accionEjecutada()) ? r.accion() : null;
        AccionResuelta.Accion accion = new AccionResuelta.Accion(r.accionEjecutada(), nombre, null, pedida,
                r.enValorBase(), r.tipo(), r.esEpica(), r.potenciada(),
                golpe == null ? null : new AccionResuelta.Tirada(golpe.ataqueResuelto(), golpe.defensaObjetivo(),
                        golpe.acierta(), golpe.indiceTabla(), golpe.porcentajeDano()));

        // Los que cambiaron de vida, y ademas el ejecutor y el objetivo: aunque
        // no se les mueva la barra, cambian su poder, sus cargas o sus efectos.
        Set<UUID> tocados = new LinkedHashSet<>();
        r.afectados().forEach(a -> tocados.add(a.id()));
        tocados.add(r.ejecutor());
        if (r.objetivo() != null) {
            tocados.add(r.objetivo());
        }
        List<AccionResuelta.Afectado> afectados = new ArrayList<>();
        for (UUID id : tocados) {
            int diferencia = r.afectados().stream().filter(a -> a.id().equals(id))
                    .mapToInt(ResolucionDeAccion.Afectado::diferencia).sum();
            partida.participante(id).map(p -> afectadoDe(p, diferencia)).ifPresent(afectados::add);
        }
        return new AccionResuelta(partida.id(), r.ejecutor(), accion, afectados);
    }

    /**
     * Los sangrados y sanaciones que actuaron al empezar un turno, cada uno
     * como una accion resuelta {@code EFECTO_POR_TURNO} (canal 1.5.0): el
     * ejecutor es quien lo causo y el nombre, el efecto.
     */
    private static List<AccionResuelta> efectosPorTurno(Partida partida, InicioDeTurno inicio) {
        List<AccionResuelta> anuncios = new ArrayList<>();
        for (EventoDeCombate evento : inicio.eventos()) {
            if (!evento.esEfectoPorTurno() || evento.combatiente() == null) {
                continue;
            }
            int cantidad = evento.cantidad() == null ? 0 : evento.cantidad();
            int diferencia = "DANO_POR_TURNO".equals(evento.tipo()) ? -cantidad : cantidad;
            UUID causante = evento.origen() != null && partida.participante(evento.origen()).isPresent()
                    ? evento.origen() : evento.combatiente();
            String nombre = evento.efecto() == null || evento.efecto().isBlank() ? "Efecto" : evento.efecto();
            partida.participante(evento.combatiente()).filter(p -> p.heroe() != null).ifPresent(p ->
                    anuncios.add(new AccionResuelta(partida.id(), causante,
                            new AccionResuelta.Accion(AccionResuelta.EFECTO_POR_TURNO, nombre, null, null, false,
                                    "EFECTO", false, false, null),
                            List.of(afectadoDe(p, diferencia)))));
        }
        return anuncios;
    }

    private static AccionResuelta.Afectado afectadoDe(ParticipanteDePartida p, int diferencia) {
        EstadoDeCombate combate = p.combate();
        int vida = p.heroe() == null ? 0 : p.heroe().vidaActual();
        int maxima = p.heroe() == null ? 1 : p.heroe().vidaMaxima();
        return new AccionResuelta.Afectado(p.idJugador(), vida, maxima, diferencia,
                combate == null ? null : combate.poderActual(),
                combate == null ? null : combate.poderMaximo(),
                combate == null ? null : combate.recargas(),
                combate == null ? null : combate.efectos());
    }

    // -------------------------------------------------------------------- apoyo

    /**
     * Si el motor puede resolver a este participante: tiene heroe y se sabe
     * de que prototipo del catalogo sale.
     */
    private static boolean combateConReglas(ParticipanteDePartida participante) {
        return participante.heroe() != null
                && participante.heroe().prototipo() != null
                && !participante.heroe().prototipo().isBlank();
    }

    /**
     * La accion basica de su heroe cuando el cliente no manda ninguna: la
     * sanacion basica si es un sanador (lo dicen sus acciones, calculadas por
     * el motor), y si no, el ataque basico.
     */
    private static String basicaDe(ParticipanteDePartida ejecutor) {
        EstadoDeCombate combate = ejecutor.combate();
        if (combate != null) {
            boolean ataca = combate.acciones().stream().anyMatch(a -> ACCION_BASICA.equals(a.codigo()));
            boolean sana = combate.acciones().stream()
                    .anyMatch(a -> MotorDeCombate.SANACION_BASICA.equals(a.codigo()));
            if (!ataca && sana) {
                return MotorDeCombate.SANACION_BASICA;
            }
        }
        return ACCION_BASICA;
    }

    private static ParticipanteDePartida participante(Partida partida, UUID id) {
        return partida.participante(id)
                .orElseThrow(() -> new SinObjetivoPosible("Ese jugador no esta en la partida."));
    }
}
