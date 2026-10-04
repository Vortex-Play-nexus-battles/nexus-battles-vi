package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResultadoDePartida;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Historial de partidas del jugador — {@code GET /partidas/mias} (1.7.0).
 *
 * <p>Las partidas en las que jugo quien firma el token, de la mas reciente a la
 * mas antigua, con el resultado desde SU punto de vista. El jugador sale del
 * token, nunca de la ruta: no hay forma de pedir el historial de otro.
 */
public class MisPartidas {

    /** Mismo tamano de pagina que el listado de salas (RNF-USA-001). */
    public static final int TAMANO_POR_DEFECTO = 16;
    public static final int TAMANO_MAXIMO = 50;

    private final RepositorioDePartidas partidas;
    private final RepositorioDeSalas salas;

    public MisPartidas(RepositorioDePartidas partidas, RepositorioDeSalas salas) {
        this.partidas = Objects.requireNonNull(partidas);
        this.salas = Objects.requireNonNull(salas);
    }

    /** Una linea del historial (esquema {@code ResumenDePartida}). */
    public record Resumen(UUID id, UUID idSala, Modalidad modalidad, EstadoPartida estado, String resultado,
                          String heroe, int participantes, Instant iniciadaEn, Instant finalizadaEn) {
    }

    /** Una pagina del historial. */
    public record Pagina(List<Resumen> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {
    }

    public Pagina ejecutar(UUID jugador, int pagina, int tamano) {
        Objects.requireNonNull(jugador, "El historial es de alguien.");
        int paginaValida = Math.max(0, pagina);
        int tamanoValido = tamano < 1 ? TAMANO_POR_DEFECTO : Math.min(tamano, TAMANO_MAXIMO);
        PaginaDePartidas encontradas = partidas.buscarPorJugador(jugador, paginaValida, tamanoValido);
        return new Pagina(
                encontradas.contenido().stream().map(p -> resumen(p, jugador)).toList(),
                encontradas.pagina(), encontradas.tamano(), encontradas.totalElementos(),
                encontradas.totalPaginas());
    }

    private Resumen resumen(Partida partida, UUID jugador) {
        Modalidad modalidad = salas.buscarPorId(partida.idSala()).map(s -> s.modalidad()).orElse(null);
        String heroe = partida.participante(jugador)
                .map(ParticipanteDePartida::heroe)
                .map(h -> h.nombre())
                .orElse(null);
        return new Resumen(partida.id(), partida.idSala(), modalidad, partida.estado(),
                resultadoPara(partida, jugador), heroe, partida.participantes().size(), partida.iniciadaEn(),
                partida.finalizadaEn());
    }

    /** VICTORIA, DERROTA o EMPATE desde el punto de vista de quien pregunta; nulo en curso. */
    static String resultadoPara(Partida partida, UUID jugador) {
        return partida.resultado().map(resultado -> {
            if (resultado == ResultadoDePartida.EMPATE) {
                return "EMPATE";
            }
            boolean gano = partida.ganadores().stream().anyMatch(p -> p.idJugador().equals(jugador));
            return gano ? "VICTORIA" : "DERROTA";
        }).orElse(null);
    }
}
