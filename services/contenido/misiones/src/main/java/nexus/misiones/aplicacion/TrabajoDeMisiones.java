package nexus.misiones.aplicacion;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import nexus.misiones.dominio.Ejecucion;
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
 * que no se pudo hacer queda en la base y lo retoma la vuelta siguiente, asi
 * que un reinicio del servicio no pierde nada. Tampoco un fallo de la consulta
 * de una fase (Mongo que no contesta) le quita su vuelta a la otra.
 *
 * <p>Pueden correr varias instancias y varias vueltas a la vez: cada
 * simulacion se reserva antes de empezar ({@link SimularEjecucion}) y cada paso
 * de entrega es idempotente de quien lo recibe ({@link LiquidarEjecucion}).
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
            try {
                if (simular.simular(vencida).isPresent()) {
                    simuladas++;
                }
            } catch (RuntimeException fallo) {
                BITACORA.warn("La ejecucion {} no se pudo simular en esta vuelta; sigue con las demas: {}",
                        vencida.id(), fallo.getMessage());
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
}
