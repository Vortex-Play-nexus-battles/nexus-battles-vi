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

    /**
     * La penalizacion provisional (decision del PO pendiente, HU-MIS-015 no la
     * cuantifica): se pierde todo lo de esta ejecucion, experiencia incluida.
     */
    public static final String PENALIZACION =
            "Pierdes todas las recompensas de esta misión, experiencia incluida.";

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
        ejecucion.cancelar(reloj.instant());
        Ejecucion guardada;
        try {
            guardada = ejecuciones.guardar(ejecucion);
        } catch (EjecucionModificadaConcurrentemente justoTermino) {
            throw new TransicionNoPermitida("La misión acaba de terminar: ya no se puede cancelar.");
        }
        Ejecucion liquidada = liquidar.liquidar(guardada);
        return new Cancelacion(liquidada, liquidada.estadoDe(PasoDeLiquidacion.LIBERACION) == EstadoDePaso.HECHO,
                PENALIZACION);
    }
}
