package nexus.misiones.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Donde viven las ejecuciones: la coleccion propia {@code ejecuciones} de la
 * base de misiones (regla 7: nadie mas la lee).
 */
public interface RepositorioDeEjecuciones {

    /**
     * Guarda con control optimista: si otra escritura cambio la ejecucion desde
     * que se leyo, lanza {@link EjecucionModificadaConcurrentemente} y no
     * guarda nada. Tambien lo lanza si el jugador ya tiene OTRA ejecucion en
     * curso de la misma mision (indice unico parcial).
     */
    Ejecucion guardar(Ejecucion ejecucion);

    Optional<Ejecucion> buscar(UUID id);

    Optional<Ejecucion> buscarPorClave(String jugadorUid, String claveIdempotencia);

    /** Todas las del jugador, de la mas reciente a la mas antigua (con tope). */
    List<Ejecucion> delJugador(String jugadorUid);

    List<Ejecucion> delJugadorEnMision(String jugadorUid, String misionId);

    List<Ejecucion> enCursoDelJugador(String jugadorUid);

    /** Cuantas empezo el jugador en esa mision desde el instante dado (intentos de desafio). */
    long iniciadasDesde(String jugadorUid, String misionId, Instant desde);

    /** En progreso con el plazo vencido: las que hay que simular. */
    List<Ejecucion> vencidas(Instant ahora, int limite);

    /** Terminadas con algun paso de entrega pendiente y ya en su turno de reintento. */
    List<Ejecucion> conLiquidacionPendiente(Instant ahora, int limite);
}
