package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.LiquidacionDeApuesta;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeLiquidaciones;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Liquidacion de la apuesta al terminar la partida — HU-JUE-014, CA-04 y CA-06.
 *
 * <p>Con ganador: se <b>cobra</b> la reserva de cada uno de los demas
 * acreditandosela al ganador, y la del propio ganador se <b>libera</b> (se
 * queda con lo suyo y con lo de los demas). En empate se liberan todas. Cada
 * operacion es idempotente del lado del libro de creditos, asi que la
 * liquidacion entera se puede repetir sin que nadie pague dos veces: es lo que
 * permite reintentarla cuando el libro no contesto a la primera.
 *
 * <p><b>Si el libro no responde</b>, la partida ya termino y no se deshace. Lo
 * que se hace es anotar la liquidacion como {@code PENDIENTE} con el motivo,
 * devolver «sin reparto» para que el aviso de fin salga igual, y dejar que
 * {@link ReintentarLiquidaciones} la cierre mas tarde. Nunca se pierde en
 * silencio: queda en la base y en la bitacora.
 *
 * <p><b>«Una sola operacion»</b> (CA-04): el libro de creditos no ofrece un
 * cobro por lotes, asi que el total le llega al ganador en tantas
 * acreditaciones como perdedores haya. Para el ganador es una sola cosa —ve el
 * total en su saldo— y para este servicio es una sola liquidacion, que o se
 * completa o queda pendiente entera.
 *
 * <p><b>Decision no definida — la maquina gana.</b> La IA no tiene bolsa
 * (RF-JUE-014: no apuesta), asi que no hay a quien pagarle. Ninguna HU dice
 * que pasa con lo apostado por los humanos en ese caso; se hace configurable
 * ({@link SiGanaLaMaquina}) y por defecto se <b>devuelve</b>, que es lo unico
 * que no le quita nada a nadie sin una regla que lo respalde. Anotado en el
 * registro de decisiones pendientes.
 */
public class LiquidarApuesta {

    private static final Logger BITACORA = LoggerFactory.getLogger(LiquidarApuesta.class);

    /** D-02 / HU-JUE-014: la clave de esta decision en el catalogo de admin-parametros. */
    public static final String CLAVE_SI_GANA_LA_MAQUINA = "salas.apuestas.si-gana-la-maquina";

    /** Que hacer con las reservas de los humanos cuando gana la IA. */
    public enum SiGanaLaMaquina {
        /** Se devuelven: nadie pierde contra la maquina. Valor por defecto. */
        LIBERAR,
        /** Se cobran sin beneficiario: la casa se queda con la apuesta. */
        CONSUMIR
    }

    private final RepositorioDeSalas salas;
    private final RepositorioDeLiquidaciones liquidaciones;
    private final CreditosDelJugador creditos;
    private final Clock reloj;
    private final Supplier<SiGanaLaMaquina> siGanaLaMaquina;

    /**
     * La politica se pide en cada liquidacion, no se fija al arrancar.
     *
     * <p>D-02 la declara configurable, y desde R12 su valor vigente vive en el
     * catalogo de admin-parametros ({@code salas.apuestas.si-gana-la-maquina}).
     * Si se guardara aqui como valor fijo, cambiarla exigiria reiniciar el
     * servicio y el parametro seria configurable solo de nombre. El proveedor
     * que pasa el cableado ya trae cache y respaldo, asi que preguntar en cada
     * liquidacion no cuesta una llamada de red.
     */
    public LiquidarApuesta(RepositorioDeSalas salas, RepositorioDeLiquidaciones liquidaciones,
                           CreditosDelJugador creditos, Clock reloj,
                           Supplier<SiGanaLaMaquina> siGanaLaMaquina) {
        this.salas = Objects.requireNonNull(salas);
        this.liquidaciones = Objects.requireNonNull(liquidaciones);
        this.creditos = Objects.requireNonNull(creditos, "Hace falta el libro de creditos.");
        this.reloj = Objects.requireNonNull(reloj);
        this.siGanaLaMaquina = Objects.requireNonNull(siGanaLaMaquina);
    }

    /** Con una politica fija: la usan las pruebas y cualquier entorno sin catalogo. */
    public LiquidarApuesta(RepositorioDeSalas salas, RepositorioDeLiquidaciones liquidaciones,
                           CreditosDelJugador creditos, Clock reloj,
                           SiGanaLaMaquina siGanaLaMaquina) {
        this(salas, liquidaciones, creditos, reloj, constante(siGanaLaMaquina));
    }

    private static Supplier<SiGanaLaMaquina> constante(SiGanaLaMaquina politica) {
        Objects.requireNonNull(politica, "Hace falta la politica de apuesta contra la maquina.");
        return () -> politica;
    }

    /**
     * La politica vigente. Nunca puede salir nula: el proveedor devuelve su
     * respaldo cuando el catalogo no responde, pero si alguien cableara uno
     * que no lo cumple, aqui se cae del lado seguro (LIBERAR: devolver lo
     * apostado es lo unico que no le quita nada a nadie sin una regla detras).
     */
    private SiGanaLaMaquina politicaVigente() {
        SiGanaLaMaquina politica = siGanaLaMaquina.get();
        if (politica == null) {
            BITACORA.warn("Sin politica para 'gana la maquina'; se libera la apuesta, que es lo que no quita nada");
            return SiGanaLaMaquina.LIBERAR;
        }
        return politica;
    }

    /**
     * Liquida la apuesta de una partida que acaba de terminar.
     *
     * @return el reparto por participante; vacio si la partida no tenia
     *         apuesta, y tambien vacio si el libro no respondio y la
     *         liquidacion quedo pendiente (se distingue en la base, no aqui:
     *         el aviso de fin sale igual en los dos casos)
     */
    public List<RepartoDeCreditos> alTerminar(Partida partida) {
        Objects.requireNonNull(partida, "Hace falta la partida que termino.");
        if (partida.estado() != EstadoPartida.FINALIZADA) {
            throw new IllegalArgumentException("Solo se liquida una partida terminada.");
        }

        Sala sala = salas.buscarPorId(partida.idSala()).orElse(null);
        if (sala == null || sala.reservasDeCreditos().isEmpty()) {
            // CA-05: sin recompensa no se reserva, no se libera ni se liquida
            // nada. Ni siquiera se anota: no hay deuda que recordar.
            return List.of();
        }

        LiquidacionDeApuesta liquidacion = liquidaciones.buscarPorPartida(partida.id())
                .orElseGet(() -> LiquidacionDeApuesta.nueva(partida.id(), sala.id(), reloj.instant()));
        if (liquidacion.estado() == LiquidacionDeApuesta.Estado.LIQUIDADA) {
            // Ya se hizo (el motor puede repetir el aviso de fin). Se devuelve el
            // mismo reparto sin volver a tocar el libro.
            return repartoDe(partida, sala);
        }
        return intentar(partida, sala, liquidacion);
    }

    /**
     * Vuelve a intentar una liquidacion que quedo pendiente.
     *
     * @return el reparto si esta vez se cerro; vacio si sigue pendiente
     */
    public Optional<List<RepartoDeCreditos>> reintentar(LiquidacionDeApuesta pendiente, Partida partida) {
        Objects.requireNonNull(pendiente);
        Objects.requireNonNull(partida);
        Sala sala = salas.buscarPorId(pendiente.idSala()).orElse(null);
        if (sala == null) {
            BITACORA.error("La liquidacion de la partida {} apunta a la sala {}, que no existe; "
                    + "hay que revisarla a mano.", pendiente.idPartida(), pendiente.idSala());
            return Optional.empty();
        }
        List<RepartoDeCreditos> reparto = intentar(partida, sala, pendiente);
        return pendiente.estado() == LiquidacionDeApuesta.Estado.LIQUIDADA
                ? Optional.of(reparto) : Optional.empty();
    }

    private List<RepartoDeCreditos> intentar(Partida partida, Sala sala, LiquidacionDeApuesta liquidacion) {
        try {
            List<RepartoDeCreditos> reparto = moverCreditos(partida, sala);
            liquidacion.liquidada(reloj.instant());
            liquidaciones.guardar(liquidacion);
            return reparto;
        } catch (RuntimeException elLibroNoRespondio) {
            liquidacion.fallo(elLibroNoRespondio.getMessage(), reloj.instant());
            liquidaciones.guardar(liquidacion);
            BITACORA.error("La apuesta de la partida {} (sala {}) no se pudo liquidar (intento {}); "
                            + "queda pendiente y se reintentara: {}",
                    partida.id(), sala.id(), liquidacion.intentos(), elLibroNoRespondio.getMessage());
            return List.of();
        }
    }

    /**
     * Mueve los creditos en el libro. Idempotente de punta a punta: cada
     * reserva se cobra o se libera una sola vez aunque esto se ejecute dos.
     */
    private List<RepartoDeCreditos> moverCreditos(Partida partida, Sala sala) {
        Map<UUID, UUID> reservas = sala.reservasDeCreditos();
        Optional<ParticipanteDePartida> ganador = partida.ganador();

        if (ganador.isEmpty()) {
            // Empate: nadie se lleva nada, todos recuperan lo suyo.
            reservas.values().forEach(creditos::liberar);
        } else if (ganador.get().esIA()) {
            if (politicaVigente() == SiGanaLaMaquina.CONSUMIR) {
                reservas.values().forEach(reserva -> creditos.consumir(reserva, null));
            } else {
                reservas.values().forEach(creditos::liberar);
            }
        } else {
            UUID idGanador = ganador.get().idJugador();
            for (Map.Entry<UUID, UUID> entrada : reservas.entrySet()) {
                if (entrada.getKey().equals(idGanador)) {
                    creditos.liberar(entrada.getValue());
                } else {
                    creditos.consumir(entrada.getValue(), idGanador);
                }
            }
        }
        return repartoDe(partida, sala);
    }

    /** Lo que gano o perdio cada uno, calculado desde la verdad de la sala y la partida. */
    private List<RepartoDeCreditos> repartoDe(Partida partida, Sala sala) {
        Map<UUID, UUID> reservas = sala.reservasDeCreditos();
        int apuesta = sala.recompensaCreditos();
        Optional<ParticipanteDePartida> ganador = partida.ganador();
        boolean ganaLaMaquina = ganador.isPresent() && ganador.get().esIA();
        boolean seDevuelve = ganador.isEmpty()
                || (ganaLaMaquina && politicaVigente() == SiGanaLaMaquina.LIBERAR);

        List<RepartoDeCreditos> reparto = new ArrayList<>();
        for (UUID jugador : reservas.keySet()) {
            int neto;
            if (seDevuelve) {
                neto = 0;
            } else if (!ganaLaMaquina && jugador.equals(ganador.get().idJugador())) {
                neto = apuesta * (reservas.size() - 1);
            } else {
                neto = -apuesta;
            }
            reparto.add(new RepartoDeCreditos(jugador, neto));
        }
        return reparto;
    }
}
