package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto del almacen de partidas.
 *
 * <p>{@link #guardar} aplica el bloqueo optimista (B7): una partida leida antes
 * de otra escritura no la pisa, y sale
 * {@link PartidaModificadaConcurrentemente}. Devuelve la partida con la marca
 * de version nueva, que es la que hay que usar para seguir.
 */
public interface RepositorioDePartidas {

    Partida guardar(Partida partida);

    Optional<Partida> buscarPorId(UUID id);

    Optional<Partida> buscarPorSala(UUID idSala);

    /**
     * Historial de un jugador, de la mas reciente a la mas antigua (1.7.0).
     * Por omision vacio, para los dobles que no lo usan.
     */
    default PaginaDePartidas buscarPorJugador(UUID idJugador, int pagina, int tamano) {
        return new PaginaDePartidas(List.of(), pagina, tamano, 0, 0);
    }

    /**
     * Partidas en curso cuyo turno se agoto antes de {@code ahora} (D-B7-14).
     * Por omision ninguna, para los dobles que no lo usan.
     */
    default List<Partida> conTurnoVencido(Instant ahora) {
        return List.of();
    }

    /**
     * Las partidas que siguen EN_CURSO y empezaron antes de {@code limite},
     * como mucho {@code lote}: una partida abandonada retenia para siempre la
     * apuesta de su sala (auditoria de DEV del 30-sep).
     */
    default List<Partida> enCursoDesde(Instant limite, int lote) {
        return List.of();
    }
}
