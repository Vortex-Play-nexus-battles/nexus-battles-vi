package nexus.misiones.aplicacion;

import java.time.Clock;
import java.time.Instant;
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
 * ninguna otra ejecucion vencida se simulaba.
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
        Instant ahora = reloj.instant();
        for (Ejecucion vencida : ejecuciones.vencidas(ahora, parametros.loteDelTrabajo())) {
            try {
                simular.simular(vencida);
            } catch (RuntimeException fallo) {
                BITACORA.warn("No se pudo simular la ejecucion {}; se aplaza: {}", vencida.id(), fallo.getMessage());
                aplazar(vencida, ahora, fallo);
            }
        }
        for (Ejecucion pendiente : ejecuciones.conLiquidacionPendiente(reloj.instant(), parametros.loteDelTrabajo())) {
            try {
                liquidar.liquidar(pendiente);
            } catch (RuntimeException fallo) {
                BITACORA.warn("No se pudo liquidar la ejecucion {}; se reintenta en la siguiente vuelta: {}",
                        pendiente.id(), fallo.getMessage());
            }
        }
    }

    /**
     * Se relee la ejecucion en vez de usar la de la vuelta: la simulacion
     * pudo dejarla terminada en memoria antes de fallar al guardarla, y eso
     * no se debe escribir. Si entre tanto el jugador la cancelo, o ya no se
     * puede ni leer ni guardar, no se aplaza nada: la vuelta siguiente vera
     * lo que haya.
     */
    private void aplazar(Ejecucion vencida, Instant ahora, RuntimeException fallo) {
        try {
            ejecuciones.buscar(vencida.id())
                    .filter(e -> e.estado() == EstadoEjecucion.EN_PROGRESO)
                    .ifPresent(e -> {
                        e.simulacionAplazada(ahora, parametros.reintentoBase(), fallo.getMessage());
                        ejecuciones.guardar(e);
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
