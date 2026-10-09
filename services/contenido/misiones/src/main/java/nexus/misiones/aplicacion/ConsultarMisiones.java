package nexus.misiones.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import nexus.misiones.dominio.Categoria;
import nexus.misiones.dominio.CatalogoDeMisiones;
import nexus.misiones.dominio.Dificultad;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.EstadoMision;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.MisionNoEncontrada;
import nexus.misiones.dominio.RepositorioDeEjecuciones;
import nexus.misiones.dominio.RepositorioDeFavoritas;
import nexus.misiones.dominio.SituacionDelJugador;

/**
 * El tablon, las destacadas del banner y el detalle (seccion 7.8.9), cada
 * mision con la situacion del jugador que pregunta.
 */
public class ConsultarMisiones {

    /** Cuantas destacadas como mucho lleva el banner rotativo. */
    static final int DESTACADAS_MAXIMAS = 10;

    private final CatalogoDeMisiones catalogo;
    private final RepositorioDeEjecuciones ejecuciones;
    private final RepositorioDeFavoritas favoritas;
    private final Clock reloj;

    public ConsultarMisiones(CatalogoDeMisiones catalogo, RepositorioDeEjecuciones ejecuciones,
                             RepositorioDeFavoritas favoritas, Clock reloj) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.ejecuciones = Objects.requireNonNull(ejecuciones);
        this.favoritas = Objects.requireNonNull(favoritas);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /**
     * @param dificultad nula = todas
     * @param estado     nulo = todos
     * @param duracion   tramo de la interfaz, o nulo
     * @param pagina     desde cero
     */
    public PaginaDeMisiones tablero(String jugadorUid, Categoria categoria, Dificultad dificultad,
                                    EstadoMision estado, String duracion, int pagina) {
        Objects.requireNonNull(categoria, "El tablon se consulta por categoria.");
        if (pagina < 0) {
            throw new IllegalArgumentException("La pagina empieza en cero.");
        }
        Vista vista = vista(jugadorUid);
        List<MisionParaJugador> filtradas = catalogo.todas().stream()
                .filter(m -> m.categoria() == categoria)
                .filter(m -> m.vigente(vista.ahora))
                .filter(m -> dificultad == null || m.dificultad() == dificultad)
                .filter(m -> m.enTramo(duracion))
                .map(vista::de)
                .filter(m -> estado == null || m.situacion().estado() == estado)
                .toList();
        int total = filtradas.size();
        int totalPaginas = (int) Math.ceil(total / (double) PaginaDeMisiones.TAMANIO);
        List<MisionParaJugador> pedidas = filtradas.stream()
                .skip((long) pagina * PaginaDeMisiones.TAMANIO)
                .limit(PaginaDeMisiones.TAMANIO)
                .toList();
        return new PaginaDeMisiones(pedidas, total, pagina, totalPaginas);
    }

    /** Las del banner: destacadas, vigentes y que el jugador no tiene bloqueadas. */
    public List<MisionParaJugador> destacadas(String jugadorUid) {
        Vista vista = vista(jugadorUid);
        return catalogo.todas().stream()
                .filter(Mision::destacada)
                .filter(m -> m.vigente(vista.ahora))
                .map(vista::de)
                .filter(m -> m.situacion().estado() != EstadoMision.BLOQUEADA)
                .limit(DESTACADAS_MAXIMAS)
                .toList();
    }

    /** @throws MisionNoEncontrada si no existe o su tiempo limitado ya paso */
    public MisionParaJugador detalle(String jugadorUid, String misionId) {
        Vista vista = vista(jugadorUid);
        Mision mision = catalogo.buscar(misionId)
                .filter(m -> m.vigente(vista.ahora))
                .orElseThrow(() -> new MisionNoEncontrada(misionId));
        return vista.de(mision);
    }

    /**
     * Intentos que le quedan al jugador en el periodo vigente (7.8.2, «limite
     * de intentos diarios o semanales»). Un intento se consume al matricular,
     * salvo que la ejecucion se cancele sin penalizacion porque su simulacion fallaba por un error del sistema.
     *
     * @return nulo si la mision no limita intentos (no es un desafio)
     */
    public Integer intentosRestantes(String jugadorUid, Mision mision) {
        if (mision.intentos() == null) {
            return null;
        }
        long usados = ejecuciones.iniciadasDesde(jugadorUid, mision.id(),
                mision.intentos().periodo().inicio(reloj.instant()));
        return (int) Math.max(0, mision.intentos().maximo() - usados);
    }

    private Vista vista(String jugadorUid) {
        List<Ejecucion> suyas = ejecuciones.delJugador(jugadorUid);
        return new Vista(reloj.instant(),
                suyas.stream().collect(Collectors.groupingBy(Ejecucion::misionId)),
                suyas.stream().filter(e -> e.estado() == EstadoEjecucion.COMPLETADA)
                        .map(Ejecucion::misionId).collect(Collectors.toSet()),
                favoritas.delJugador(jugadorUid),
                catalogo.todas().stream().collect(Collectors.toMap(Mision::id, Mision::nombre, (a, b) -> a)));
    }

    /** Lo del jugador que hace falta para pintar cualquier mision, leido una vez. */
    private record Vista(Instant ahora, Map<String, List<Ejecucion>> porMision, Set<String> completadas,
                         Set<String> favoritas, Map<String, String> nombres) {

        MisionParaJugador de(Mision mision) {
            SituacionDelJugador situacion = SituacionDelJugador.calcular(mision,
                    porMision.getOrDefault(mision.id(), List.of()), completadas, nombres::get, ahora);
            return new MisionParaJugador(mision, situacion, favoritas.contains(mision.id()));
        }
    }
}
