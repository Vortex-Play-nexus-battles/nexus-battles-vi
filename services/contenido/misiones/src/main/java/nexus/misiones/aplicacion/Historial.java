package nexus.misiones.aplicacion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import nexus.misiones.dominio.Categoria;

/**
 * El historial de misiones de un jugador (seccion 7.8.8): las terminadas con
 * fecha, estadisticas por categoria, mejores tiempos, la coleccion de epicas
 * de Master y el progreso en la historia.
 */
public record Historial(
        List<EjecucionConMision> terminadas,
        List<PorCategoria> porCategoria,
        List<MejorTiempo> mejoresTiempos,
        List<EpicaObtenida> epicas,
        List<Cadena> cadenas) {

    public record PorCategoria(Categoria categoria, int completadas, int fallidas) {
    }

    public record MejorTiempo(String misionId, String nombre, Duration duracion) {
    }

    public record EpicaObtenida(String nombre, String master, Instant obtenidaEn) {
    }

    public record Cadena(String nombre, int completadas, int total) {
    }
}
