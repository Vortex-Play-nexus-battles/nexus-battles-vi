package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditadorDePartidas.Acreditacion;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditadorDePartidas.InformeDePartida;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditadorDePartidas.TipoDePartida;
import com.nexusbattles.plataforma.salaspartidas.chat.SancionesDelJugador;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RecompensaDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeRecompensas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Recompensa por jugar al terminar la partida — HU-JUE-012 (RF-JUE-012).
 *
 * <p>Este servicio sabe quien jugo, quien gano y de que modalidad era la
 * partida; el libro de creditos (ms-finanzas) sabe cuanto vale eso: 2 al
 * ganador de un uno contra uno, 4 a cada ganador de una grupal, 1 por
 * participar, y los cofres de HU-JUE-013. Aqui se <b>informa</b> y se anuncia
 * lo que el libro acredito; no se calcula ninguna cifra.
 *
 * <p><b>Lo que se informa (CA-01, CA-02):</b> solo los humanos. La maquina no
 * tiene cuenta y la ficha oficial no dice si sus cupos ganan creditos, asi
 * que no se le pide nada al libro por ella (CA-06, decision D-18). Los
 * ganadores son los humanos en pie: uno, o todo el equipo ganador (la regla
 * para equipos la aplica el libro; si el PO la cambia, no se toca esto).
 * Si la maquina gano, o hubo empate, no hay ganadores y todos reciben lo de
 * participar.
 *
 * <p><b>Sancionados (CA-04):</b> se consulta la sancion activa de cada humano
 * (mismo hecho que silencia el chat, D-14) y se informa al libro, que es
 * quien los excluye. Si el servicio de sanciones no responde, no se asume
 * «sin sancion»: el informe entero queda pendiente y se reintenta, porque
 * acreditar a un sancionado no se puede deshacer y esperar si.
 *
 * <p><b>Si el libro no responde (CA-05):</b> la partida ya termino y no se
 * deshace. Se anota la recompensa como {@code PENDIENTE} con el motivo, el
 * aviso de fin sale sin ella, y {@link ReintentarLiquidaciones} la vuelve a
 * informar con el mismo {@code idPartida}; un {@code 409 partida-ya-procesada}
 * en el reintento significa que la primera si entro, y se cierra sin anunciar
 * nada nuevo. Nunca se pierde en silencio.
 */
public class AcreditarRecompensa {

    private static final Logger BITACORA = LoggerFactory.getLogger(AcreditarRecompensa.class);

    private final RepositorioDeSalas salas;
    private final RepositorioDeRecompensas recompensas;
    private final AcreditadorDePartidas libro;
    private final SancionesDelJugador sanciones;
    private final Clock reloj;

    public AcreditarRecompensa(RepositorioDeSalas salas, RepositorioDeRecompensas recompensas,
                               AcreditadorDePartidas libro, SancionesDelJugador sanciones, Clock reloj) {
        this.salas = Objects.requireNonNull(salas);
        this.recompensas = Objects.requireNonNull(recompensas);
        this.libro = Objects.requireNonNull(libro, "Hace falta el libro de creditos.");
        this.sanciones = Objects.requireNonNull(sanciones, "Hace falta saber quien esta sancionado.");
        this.reloj = Objects.requireNonNull(reloj);
    }

    /**
     * Informa al libro la partida que acaba de terminar.
     *
     * @return lo acreditado por participante; vacio si la partida no tenia
     *         humanos que recompensar, si el libro no respondio y quedo
     *         pendiente, o si ya se habia acreditado antes (el aviso ya salio
     *         con el detalle la primera vez)
     */
    public List<CreditoPorPartida> alTerminar(Partida partida) {
        Objects.requireNonNull(partida, "Hace falta la partida que termino.");
        if (partida.estado() != EstadoPartida.FINALIZADA) {
            throw new IllegalArgumentException("Solo se recompensa una partida terminada.");
        }
        if (humanos(partida).isEmpty()) {
            return List.of();
        }
        RecompensaDePartida recompensa = recompensas.buscarPorPartida(partida.id())
                .orElseGet(() -> RecompensaDePartida.nueva(partida.id(), partida.idSala(), reloj.instant()));
        if (recompensa.estado() == RecompensaDePartida.Estado.ACREDITADA) {
            // El motor puede repetir el aviso de fin; el libro ya tiene la
            // partida y no hay nada nuevo que anunciar.
            return List.of();
        }
        return intentar(partida, recompensa);
    }

    /**
     * Vuelve a informar una recompensa que quedo pendiente.
     *
     * @return lo acreditado si esta vez entro (vacio si el libro ya la tenia);
     *         ausente si sigue pendiente
     */
    public Optional<List<CreditoPorPartida>> reintentar(RecompensaDePartida pendiente, Partida partida) {
        Objects.requireNonNull(pendiente);
        Objects.requireNonNull(partida);
        List<CreditoPorPartida> creditos = intentar(partida, pendiente);
        return pendiente.estado() == RecompensaDePartida.Estado.ACREDITADA
                ? Optional.of(creditos) : Optional.empty();
    }

    private List<CreditoPorPartida> intentar(Partida partida, RecompensaDePartida recompensa) {
        try {
            Acreditacion acreditacion = libro.acreditar(informeDe(partida));
            recompensa.acreditada(reloj.instant());
            recompensas.guardar(recompensa);
            if (acreditacion.yaProcesada()) {
                BITACORA.info("El libro ya tenia la recompensa de la partida {}: se da por acreditada.",
                        partida.id());
                return List.of();
            }
            if (!acreditacion.sancionadosExcluidos().isEmpty()) {
                BITACORA.info("Partida {}: sin recompensa por sancion activa: {}", partida.id(),
                        acreditacion.sancionadosExcluidos());
            }
            return acreditacion.creditos();
        } catch (RuntimeException elLibroNoRespondio) {
            recompensa.fallo(elLibroNoRespondio.getMessage(), reloj.instant());
            recompensas.guardar(recompensa);
            BITACORA.error("La recompensa de la partida {} no se pudo acreditar (intento {}); "
                            + "queda pendiente y se reintentara: {}",
                    partida.id(), recompensa.intentos(), elLibroNoRespondio.getMessage());
            return List.of();
        }
    }

    /** Lo que se le cuenta al libro, desde la verdad de la partida y su sala. */
    InformeDePartida informeDe(Partida partida) {
        List<ParticipanteDePartida> humanos = humanos(partida);
        List<UUID> ganadores = partida.ganadores().stream()
                .filter(p -> !p.esIA())
                .map(ParticipanteDePartida::idJugador)
                .toList();
        List<InformeDePartida.Jugador> jugadores = humanos.stream()
                .map(p -> new InformeDePartida.Jugador(p.idJugador(), sanciones.estaSilenciado(p.idJugador())))
                .toList();
        return new InformeDePartida(partida.id(), tipoDe(partida), ganadores, jugadores);
    }

    /**
     * Uno contra uno o grupal, por la modalidad de la sala (RF-JUE-004). Si la
     * sala ya no esta, por cuantos jugaron: mas de dos es grupal.
     */
    private TipoDePartida tipoDe(Partida partida) {
        Optional<Modalidad> modalidad = salas.buscarPorId(partida.idSala()).map(s -> s.modalidad());
        if (modalidad.isPresent()) {
            return modalidad.get() == Modalidad.HASTA_SEIS ? TipoDePartida.GRUPAL : TipoDePartida.UNO_A_UNO;
        }
        return partida.participantes().size() > 2 ? TipoDePartida.GRUPAL : TipoDePartida.UNO_A_UNO;
    }

    private static List<ParticipanteDePartida> humanos(Partida partida) {
        return partida.participantes().stream().filter(p -> !p.esIA()).toList();
    }
}
