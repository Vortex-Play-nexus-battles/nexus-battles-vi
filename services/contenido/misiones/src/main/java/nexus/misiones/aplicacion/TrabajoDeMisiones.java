package nexus.misiones.aplicacion;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EjecucionModificadaConcurrentemente;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.RepositorioDeEjecuciones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El trabajo en segundo plano (7.8.12, «procesamiento asincrono: ejecucion de
 * simulaciones en segundo plano; cola de misiones para procesamiento
 * eficiente»). La «cola» es la propia coleccion de ejecuciones: en cada vuelta
 * simula las que vencieron y liquida las que tienen pasos pendientes, en lotes.
 *
 * <p>Un fallo en una ejecucion no para a las demas: se anota y se sigue. Lo
 * que no se pudo hacer queda en la base y lo retoma una vuelta posterior, asi
 * que un reinicio del servicio no pierde nada. Una simulacion que falla se
 * APLAZA ({@link Ejecucion#simulacionAplazada}): sin eso, las que no se pueden
 * simular, que son las mas antiguas, ocupaban el lote entero en cada vuelta y
 * ninguna otra ejecucion vencida se simulaba. Tampoco un fallo de la consulta
 * de una fase (Mongo que no contesta) le quita su vuelta a la otra.
 *
 * <p>Pueden correr varias instancias y varias vueltas a la vez (HU-SIM-007):
 * cada simulacion se reserva antes de empezar ({@link SimularEjecucion}) y
 * cada paso de entrega es idempotente de quien lo recibe
 * ({@link LiquidarEjecucion}).
 */
public class TrabajoDeMisiones {

    private static final Logger BITACORA = LoggerFactory.getLogger(TrabajoDeMisiones.class);

    private final RepositorioDeEjecuciones ejecuciones;
    private final SimularEjecucion simular;
    private final LiquidarEjecucion liquidar;
    private final ParametrosDeMisiones parametros;
    private final Clock reloj;

    public TrabajoDeMisiones(RepositorioDeEjecuciones ejecuciones, SimularEjecucion simular,
                             LiquidarEjecucion liquidar, ParametrosDeMisiones parametros, Clock reloj) {
        this.ejecuciones = Objects.requireNonNull(ejecuciones);
        this.simular = Objects.requireNonNull(simular);
        this.liquidar = Objects.requireNonNull(liquidar);
        this.parametros = Objects.requireNonNull(parametros);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public void ejecutar() {
        int simuladas = simularLasVencidas();
        int liquidadas = liquidarLasPendientes();
        if (simuladas + liquidadas > 0) {
            BITACORA.info("Vuelta del trabajo: {} ejecuciones simuladas, {} liquidadas", simuladas, liquidadas);
        }
    }

    private int simularLasVencidas() {
        List<Ejecucion> vencidas;
        try {
            vencidas = ejecuciones.vencidas(reloj.instant(), parametros.loteDelTrabajo());
        } catch (RuntimeException sinConsulta) {
            BITACORA.warn("No se pudieron consultar las ejecuciones vencidas; se sigue con las entregas y se"
                    + " reintenta en la siguiente vuelta: {}", sinConsulta.getMessage());
            return 0;
        }
        int simuladas = 0;
        for (Ejecucion vencida : vencidas) {
            // La reserva se anota en la propia ejecucion (cuenta un intento): se recuerda cuantos llevaba.
            int reservasAntes = vencida.intentosDeSimulacion();
            try {
                if (simular.simular(vencida).isPresent()) {
                    simuladas++;
                }
            } catch (RuntimeException fallo) {
                BITACORA.warn("No se pudo simular la ejecucion {}; se aplaza: {}", vencida.id(), fallo.getMessage());
                aplazar(vencida, reservasAntes, fallo);
            }
        }
        return simuladas;
    }

    private int liquidarLasPendientes() {
        List<Ejecucion> pendientes;
        try {
            pendientes = ejecuciones.conLiquidacionPendiente(reloj.instant(), parametros.loteDelTrabajo());
        } catch (RuntimeException sinConsulta) {
            BITACORA.warn("No se pudieron consultar las entregas pendientes; se reintenta en la siguiente vuelta: {}",
                    sinConsulta.getMessage());
            return 0;
        }
        int liquidadas = 0;
        for (Ejecucion pendiente : pendientes) {
            try {
                if (!liquidar.liquidar(pendiente).liquidacionPendiente()) {
                    liquidadas++;
                }
            } catch (RuntimeException fallo) {
                BITACORA.warn("No se pudo liquidar la ejecucion {}; se reintenta en la siguiente vuelta: {}",
                        pendiente.id(), fallo.getMessage());
            }
        }
        return liquidadas;
    }

    /**
     * Se relee la ejecucion en vez de usar la de la vuelta: la simulacion
     * pudo dejarla terminada en memoria antes de fallar al guardarla, y eso
     * no se debe escribir. Si entre tanto el jugador la cancelo, o ya no se
     * puede ni leer ni guardar, no se aplaza nada: la vuelta siguiente vera
     * lo que haya.
     *
     * <p>Y solo se aplaza la reserva propia (HU-SIM-007): si la simulacion
     * tardo tanto que el arriendo vencio y otra vuelta reservo la ejecucion
     * (mas reservas de las que esta vuelta pudo hacer), la espera de este
     * fallo no debe pisar el arriendo de la otra.
     *
     * @param reservasAntes las reservas que llevaba la ejecucion antes de esta vuelta; la propia suma una
     */
    private void aplazar(Ejecucion vencida, int reservasAntes, RuntimeException fallo) {
        try {
            ejecuciones.buscar(vencida.id())
                    .filter(e -> e.estado() == EstadoEjecucion.EN_PROGRESO)
                    .filter(e -> {
                        boolean esLaNuestra = e.intentosDeSimulacion() <= reservasAntes + 1;
                        if (!esLaNuestra) {
                            BITACORA.info("Ejecucion {}: fallo la simulacion, pero otra vuelta ya la tiene reservada;"
                                    + " no se aplaza", vencida.id());
                        }
                        return esLaNuestra;
                    })
                    .ifPresent(e -> {
                        e.simulacionAplazada(reloj.instant(), parametros.reintentoBase(), fallo.getMessage());
                        Ejecucion guardada = ejecuciones.guardar(e);
                        BITACORA.info("Ejecucion {}: simulacion aplazada (intento fallido {}); se reintenta a las {}",
                                guardada.id(), guardada.intentosDeLiquidacion(), guardada.proximoIntento());
                    });
        } catch (EjecucionModificadaConcurrentemente cambio) {
            BITACORA.info("Ejecucion {} cambio mientras se aplazaba su simulacion; se relee la proxima vuelta",
                    vencida.id());
        } catch (RuntimeException sinGuardar) {
            BITACORA.warn("No se pudo aplazar la simulacion de la ejecucion {}: {}", vencida.id(),
                    sinGuardar.getMessage());
        }
    }
}
