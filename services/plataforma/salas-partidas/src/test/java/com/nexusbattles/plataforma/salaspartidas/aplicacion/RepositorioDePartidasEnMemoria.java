package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaModificadaConcurrentemente;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble en memoria del almacen de partidas.
 *
 * <p>No es un mock: implementa el puerto y se comporta como un almacen, incluida
 * la unicidad de una partida por sala y, desde B7, el bloqueo optimista: una
 * copia leida antes de otra escritura no se guarda, y cada escritura devuelve
 * la partida con la marca nueva. El adaptador real contra PostgreSQL se prueba
 * aparte, contra una base de datos de verdad.
 */
class RepositorioDePartidasEnMemoria implements RepositorioDePartidas {

    private final Map<UUID, Partida> almacen = new LinkedHashMap<>();

    @Override
    public Partida guardar(Partida partida) {
        Partida actual = almacen.get(partida.id());
        if (actual != null && actual.version() != partida.version()) {
            throw new PartidaModificadaConcurrentemente(partida.id());
        }
        Partida guardada = Partida.rehidratar(partida.id(), partida.idSala(), partida.estado(),
                partida.participantes(), partida.turnoActual(), partida.recompensaEnJuego(), partida.iniciadaEn(),
                partida.version() + 1, partida.semillaDelOrden(), partida.finalizadaEn(), partida.turnoVenceEn());
        almacen.put(partida.id(), guardada);
        return copia(guardada);
    }

    @Override
    public Optional<Partida> buscarPorId(UUID id) {
        return Optional.ofNullable(almacen.get(id)).map(RepositorioDePartidasEnMemoria::copia);
    }

    @Override
    public Optional<Partida> buscarPorSala(UUID idSala) {
        return almacen.values().stream()
                .filter(partida -> partida.idSala().equals(idSala))
                .findFirst()
                .map(RepositorioDePartidasEnMemoria::copia);
    }

    @Override
    public PaginaDePartidas buscarPorJugador(UUID idJugador, int pagina, int tamano) {
        List<Partida> suyas = almacen.values().stream()
                .filter(p -> p.esParticipante(idJugador))
                .sorted(Comparator.comparing(Partida::iniciadaEn).reversed())
                .toList();
        int desde = Math.min(pagina * tamano, suyas.size());
        int hasta = Math.min(desde + tamano, suyas.size());
        int paginas = suyas.isEmpty() ? 0 : (suyas.size() + tamano - 1) / tamano;
        return new PaginaDePartidas(suyas.subList(desde, hasta).stream().map(RepositorioDePartidasEnMemoria::copia)
                .toList(), pagina, tamano, suyas.size(), paginas);
    }

    @Override
    public List<Partida> conTurnoVencido(Instant ahora) {
        return almacen.values().stream()
                .filter(p -> p.estado() == EstadoPartida.EN_CURSO && p.turnoVenceEn() != null
                        && !p.turnoVenceEn().isAfter(ahora))
                .map(RepositorioDePartidasEnMemoria::copia)
                .toList();
    }

    @Override
    public List<Partida> enCursoDesde(Instant limite, int lote) {
        return almacen.values().stream()
                .filter(p -> p.estado() == EstadoPartida.EN_CURSO && !p.iniciadaEn().isAfter(limite))
                .limit(lote)
                .map(RepositorioDePartidasEnMemoria::copia)
                .toList();
    }

    /** Cada lectura es una copia, como lo seria una fila leida de la base. */
    private static Partida copia(Partida p) {
        return Partida.rehidratar(p.id(), p.idSala(), p.estado(), p.participantes(), p.turnoActual(),
                p.recompensaEnJuego(), p.iniciadaEn(), p.version(), p.semillaDelOrden(), p.finalizadaEn(),
                p.turnoVenceEn());
    }
}
