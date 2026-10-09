package nexus.misiones.aplicacion;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EjecucionModificadaConcurrentemente;
import nexus.misiones.dominio.EjecucionNoEncontrada;
import nexus.misiones.dominio.EstadoDePaso;
import nexus.misiones.dominio.PasoDeLiquidacion;
import nexus.misiones.dominio.RepositorioDeEjecuciones;
import nexus.misiones.dominio.TransicionNoPermitida;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cancelar una mision en curso (7.8.7, «Abandonada: cancelada por el jugador,
 * con penalizacion»; 7.8.9, «Cancelar mision, con advertencia de
 * penalizacion»; HU-MIS-015).
 *
 * <p>Primero se guarda la cancelacion —es la decision del jugador y manda—, y
 * despues se intenta liberar al heroe en ese mismo momento. Si el inventario no
 * contesta, la mision queda Abandonada igual y el trabajo en segundo plano
 * termina la liberacion.
 */
public class CancelarEjecucion {

    private static final Logger BITACORA = LoggerFactory.getLogger(CancelarEjecucion.class);

    /**
     * La penalizacion provisional (decision del PO pendiente, HU-MIS-015 no la
     * cuantifica): se pierde todo lo de esta ejecucion, experiencia incluida.
     */
    public static final String PENALIZACION =
            "Pierdes todas las recompensas de esta misión, experiencia incluida.";

    /**
     * Lo que se le dice al jugador cuando la simulacion de su mision viene fallando por un error del sistema
     * (decision del PO, 2026-10-06). Empieza por «Ninguna» porque la interfaz lo muestra tras «Penalización:». Es
     * honesto con lo que se pierde: una simulacion que no se pudo hacer no calculo recompensas, asi que no se
     * promete ninguna; lo que no ocurre es que el abandono cuente contra el jugador.
     */
    public static final String SIN_PENALIZACION =
            "Ninguna. La simulación de esta misión falló por un error del sistema, no por ti: "
                    + "cancelarla no cuenta como uno de tus intentos.";

    /**
     * Lo que le cuesta al jugador cancelar ESTA ejecucion: nada si su simulacion viene fallando por un error del
     * sistema (o si ya se cancelo asi), la penalizacion de siempre en cualquier otro caso. Es el texto que el
     * tablon de misiones en curso ensena en el dialogo de cancelar y el que contesta la cancelacion.
     */
    public static String penalizacionDe(Ejecucion ejecucion) {
        return ejecucion.simulacionFallando() || ejecucion.canceladaSinPenalizacion() ? SIN_PENALIZACION
                : PENALIZACION;
    }

    private final RepositorioDeEjecuciones ejecuciones;
    private final LiquidarEjecucion liquidar;
    private final Clock reloj;

    public CancelarEjecucion(RepositorioDeEjecuciones ejecuciones, LiquidarEjecucion liquidar, Clock reloj) {
        this.ejecuciones = Objects.requireNonNull(ejecuciones);
        this.liquidar = Objects.requireNonNull(liquidar);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public Cancelacion cancelar(String jugadorUid, UUID ejecucionId) {
        Ejecucion ejecucion = ejecuciones.buscar(ejecucionId)
                .filter(e -> e.jugadorUid().equals(jugadorUid))
                .orElseThrow(EjecucionNoEncontrada::new);
        String falloDelSistema = ejecucion.ultimoError();
        ejecucion.cancelar(reloj.instant());
        if (ejecucion.canceladaSinPenalizacion()) {
            BITACORA.info("Ejecucion {} cancelada sin penalizacion: su simulacion venia fallando por un error del"
                    + " sistema ({})", ejecucion.id(), falloDelSistema);
        }
        Ejecucion guardada;
        try {
            guardada = ejecuciones.guardar(ejecucion);
        } catch (EjecucionModificadaConcurrentemente justoTermino) {
            throw new TransicionNoPermitida("La misión acaba de terminar: ya no se puede cancelar.");
        }
        Ejecucion liquidada = liquidar.liquidar(guardada);
        return new Cancelacion(liquidada, liquidada.estadoDe(PasoDeLiquidacion.LIBERACION) == EstadoDePaso.HECHO,
                penalizacionDe(liquidada));
    }
}
