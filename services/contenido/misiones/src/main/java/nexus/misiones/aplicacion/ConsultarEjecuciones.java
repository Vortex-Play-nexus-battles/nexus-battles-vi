package nexus.misiones.aplicacion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import nexus.misiones.dominio.Categoria;
import nexus.misiones.dominio.CatalogoDeMisiones;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EjecucionNoEncontrada;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.MisionNoEncontrada;
import nexus.misiones.dominio.RecompensasDeEjecucion;
import nexus.misiones.dominio.RepositorioDeEjecuciones;
import nexus.misiones.dominio.SinReporteTodavia;

/**
 * Las ejecuciones de un jugador: en curso (7.8.9), el reporte y el historial (7.8.8).
 *
 * <p>Una ejecucion de una mision que la semilla ya no publica no se ensena en
 * las listas: no hay nombre ni categoria con que pintarla, y el trabajo en
 * segundo plano ya la cierra y libera al heroe. Su reporte responde que la
 * mision no esta publicada. Las epicas que gano, en cambio, siguen en la
 * coleccion del jugador: son suyas aunque la mision desaparezca.
 */
public class ConsultarEjecuciones {

    /** La historia es una narrativa lineal (7.8.2): una sola cadena con todas sus misiones. */
    static final String CADENA_DE_LA_HISTORIA = "Historia";

    private final CatalogoDeMisiones catalogo;
    private final RepositorioDeEjecuciones ejecuciones;

    public ConsultarEjecuciones(CatalogoDeMisiones catalogo, RepositorioDeEjecuciones ejecuciones) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.ejecuciones = Objects.requireNonNull(ejecuciones);
    }

    public List<EjecucionConMision> enCurso(String jugadorUid) {
        return ejecuciones.enCursoDelJugador(jugadorUid).stream()
                .sorted(Comparator.comparing(Ejecucion::terminaEn))
                .map(this::conMision)
                .filter(t -> t.mision() != null)
                .toList();
    }

    /**
     * @throws EjecucionNoEncontrada si no existe o es de otro jugador
     * @throws SinReporteTodavia     si sigue en curso o se abandono
     * @throws MisionNoEncontrada    si su mision ya no esta publicada
     */
    public EjecucionConMision reporte(String jugadorUid, UUID ejecucionId) {
        Ejecucion ejecucion = ejecuciones.buscar(ejecucionId)
                .filter(e -> e.jugadorUid().equals(jugadorUid))
                .orElseThrow(EjecucionNoEncontrada::new);
        if (!ejecucion.estado().tieneReporte()) {
            throw new SinReporteTodavia(ejecucion.estado());
        }
        EjecucionConMision reporte = conMision(ejecucion);
        if (reporte.mision() == null) {
            throw new MisionNoEncontrada(ejecucion.misionId());
        }
        return reporte;
    }

    public Historial historial(String jugadorUid) {
        List<EjecucionConMision> conReporte = ejecuciones.delJugador(jugadorUid).stream()
                .filter(e -> e.estado().tieneReporte())
                .sorted(Comparator.comparing(Ejecucion::terminadaEn).reversed())
                .map(this::conMision)
                .toList();
        List<EjecucionConMision> terminadas = conReporte.stream().filter(t -> t.mision() != null).toList();

        List<Historial.PorCategoria> porCategoria = new ArrayList<>();
        for (Categoria categoria : Categoria.values()) {
            int completadas = 0;
            int fallidas = 0;
            for (EjecucionConMision t : terminadas) {
                if (t.mision() == null || t.mision().categoria() != categoria) {
                    continue;
                }
                if (t.ejecucion().estado() == EstadoEjecucion.COMPLETADA) {
                    completadas++;
                } else {
                    fallidas++;
                }
            }
            porCategoria.add(new Historial.PorCategoria(categoria, completadas, fallidas));
        }

        Map<String, Historial.MejorTiempo> mejores = new LinkedHashMap<>();
        for (EjecucionConMision t : terminadas) {
            if (t.ejecucion().estado() != EstadoEjecucion.COMPLETADA) {
                continue;
            }
            Duration duracion = Duration.between(t.ejecucion().iniciadaEn(), t.ejecucion().terminadaEn());
            String id = t.ejecucion().misionId();
            Historial.MejorTiempo actual = mejores.get(id);
            if (actual == null || duracion.compareTo(actual.duracion()) < 0) {
                mejores.put(id, new Historial.MejorTiempo(id, nombreDe(t), duracion));
            }
        }

        List<Historial.EpicaObtenida> epicas = new ArrayList<>();
        for (EjecucionConMision t : conReporte) {
            RecompensasDeEjecucion recompensas = t.ejecucion().recompensas();
            if (recompensas == null) {
                continue;
            }
            for (RecompensasDeEjecucion.EpicaGanada epica : recompensas.epicas()) {
                epicas.add(new Historial.EpicaObtenida(epica.nombre(), epica.master(), t.ejecucion().terminadaEn()));
            }
        }

        List<Mision> deLaHistoria = catalogo.todas().stream()
                .filter(m -> m.categoria() == Categoria.HISTORIA)
                .toList();
        long historiaCompletada = deLaHistoria.stream()
                .filter(m -> terminadas.stream().anyMatch(t -> t.ejecucion().misionId().equals(m.id())
                        && t.ejecucion().estado() == EstadoEjecucion.COMPLETADA))
                .count();
        List<Historial.Cadena> cadenas = deLaHistoria.isEmpty()
                ? List.of()
                : List.of(new Historial.Cadena(CADENA_DE_LA_HISTORIA, (int) historiaCompletada, deLaHistoria.size()));

        return new Historial(terminadas, porCategoria, List.copyOf(mejores.values()), epicas, cadenas);
    }

    private EjecucionConMision conMision(Ejecucion ejecucion) {
        return new EjecucionConMision(ejecucion, catalogo.buscar(ejecucion.misionId()).orElse(null));
    }

    private static String nombreDe(EjecucionConMision t) {
        return t.mision() == null ? t.ejecucion().misionId() : t.mision().nombre();
    }

}
