package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Estadisticas de juego de una cuenta — {@code GET /partidas/jugadores/{idJugador}/estadisticas}
 * (salas-partidas.yaml 1.11.0, §7.3.4 «estadisticas de juego y actividad»).
 *
 * <p>Las cuenta este servicio porque es el dueño de las partidas: la ficha
 * administrativa del usuario las pide aqui y no las deduce de ninguna otra
 * base. Las partidas terminadas se reparten en victorias, derrotas y empates
 * desde el punto de vista del jugador (la misma regla que su historial); las
 * que siguen en curso se cuentan aparte, sin resultado.
 *
 * <p>Se recorre el historial del jugador por paginas, como mucho
 * {@link #LIMITE_DE_PARTIDAS}: si tiene mas, la respuesta lo dice con
 * {@code parcial} en vez de aparentar un total que no se conto.
 */
public class EstadisticasDeJugador {

    /** Cuantas partidas se cuentan como mucho en una consulta. */
    public static final int LIMITE_DE_PARTIDAS = 1000;

    /** Partidas que se leen por vuelta. */
    static final int LOTE = 100;

    private final RepositorioDePartidas partidas;
    private final RepositorioDeSalas salas;

    public EstadisticasDeJugador(RepositorioDePartidas partidas, RepositorioDeSalas salas) {
        this.partidas = Objects.requireNonNull(partidas);
        this.salas = Objects.requireNonNull(salas);
    }

    /** El recuento de una modalidad (esquema {@code EstadisticasPorModalidad}). */
    public record PorModalidad(int jugadas, int victorias, int derrotas, int empates) {

        static PorModalidad vacia() {
            return new PorModalidad(0, 0, 0, 0);
        }

        PorModalidad con(String resultado) {
            return new PorModalidad(jugadas + 1,
                    victorias + ("VICTORIA".equals(resultado) ? 1 : 0),
                    derrotas + ("DERROTA".equals(resultado) ? 1 : 0),
                    empates + ("EMPATE".equals(resultado) ? 1 : 0));
        }
    }

    /**
     * Las estadisticas (esquema {@code EstadisticasDeJugador}).
     *
     * @param jugadas         partidas terminadas, con resultado
     * @param enCurso         partidas que siguen jugandose
     * @param porModalidad    las terminadas, por modalidad conocida
     * @param ultimaPartidaEn cuando empezo la mas reciente; nulo si nunca jugo
     * @param parcial         {@code true} si habia mas de {@link #LIMITE_DE_PARTIDAS}
     */
    public record Estadisticas(UUID idJugador, int jugadas, int victorias, int derrotas, int empates,
                               int enCurso, Map<Modalidad, PorModalidad> porModalidad,
                               Instant ultimaPartidaEn, boolean parcial) {
    }

    public Estadisticas ejecutar(UUID jugador) {
        Objects.requireNonNull(jugador, "Las estadisticas son de alguien.");
        Map<Modalidad, PorModalidad> porModalidad = new EnumMap<>(Modalidad.class);
        int victorias = 0;
        int derrotas = 0;
        int empates = 0;
        int enCurso = 0;
        int contadas = 0;
        Instant ultima = null;
        boolean parcial = false;

        int pagina = 0;
        while (true) {
            PaginaDePartidas lote = partidas.buscarPorJugador(jugador, pagina, LOTE);
            for (Partida partida : lote.contenido()) {
                if (contadas == LIMITE_DE_PARTIDAS) {
                    parcial = true;
                    break;
                }
                contadas++;
                if (ultima == null || partida.iniciadaEn().isAfter(ultima)) {
                    ultima = partida.iniciadaEn();
                }
                String resultado = MisPartidas.resultadoPara(partida, jugador);
                if (resultado == null) {
                    enCurso += partida.estado() == EstadoPartida.EN_CURSO ? 1 : 0;
                    continue;
                }
                switch (resultado) {
                    case "VICTORIA" -> victorias++;
                    case "DERROTA" -> derrotas++;
                    default -> empates++;
                }
                Modalidad modalidad = salas.buscarPorId(partida.idSala()).map(s -> s.modalidad()).orElse(null);
                if (modalidad != null) {
                    porModalidad.merge(modalidad, PorModalidad.vacia().con(resultado),
                            (actual, nueva) -> actual.con(resultado));
                }
            }
            pagina++;
            if (parcial || lote.contenido().isEmpty() || pagina >= lote.totalPaginas()) {
                break;
            }
        }

        return new Estadisticas(jugador, victorias + derrotas + empates, victorias, derrotas, empates, enCurso,
                porModalidad, ultima, parcial);
    }
}
