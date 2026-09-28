package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta;
import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;

import java.util.ArrayList;
import java.util.List;

/** Canal de partida que anota lo anunciado, para comprobar el «cuando» y no solo el «que». */
class CanalDePartidaEspia implements CanalDePartida {

    /** Un anuncio publicado: su tipo y la partida a la que se refiere. */
    record Anuncio(String tipo, Partida partida) {
    }

    final List<Anuncio> anuncios = new ArrayList<>();

    /** Cada accion resuelta anunciada, en orden (B7: con su detalle). */
    final List<AccionResuelta> acciones = new ArrayList<>();

    /** Por que paso el turno en cada anuncio de turno, en orden (canal 1.5.0). */
    final List<String> motivos = new ArrayList<>();

    /** Reparto que acompano a cada aviso de fin, en orden. */
    final List<List<com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos>> repartos = new ArrayList<>();

    /** Recompensa por jugar (HU-JUE-012) de cada aviso de fin, en orden. */
    final List<List<com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida>> recompensas = new ArrayList<>();

    @Override
    public void anunciarAccionResuelta(AccionResuelta accion) {
        anuncios.add(new Anuncio("accion", null));
        acciones.add(accion);
    }

    @Override
    public void anunciarInicio(com.nexusbattles.plataforma.salaspartidas.dominio.Sala sala,
                               Partida partida) {
        anuncios.add(new Anuncio("inicio", partida));
    }

    @Override
    public void anunciarTurno(Partida partida) {
        anunciarTurno(partida, null);
    }

    @Override
    public void anunciarTurno(Partida partida, String motivo) {
        anuncios.add(new Anuncio("turno", partida));
        motivos.add(motivo);
    }

    @Override
    public void anunciarFin(Partida partida,
                            java.util.List<com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos> reparto) {
        anunciarFin(partida, reparto, List.of());
    }

    @Override
    public void anunciarFin(Partida partida,
                            java.util.List<com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos> reparto,
                            java.util.List<com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida> recompensa) {
        anuncios.add(new Anuncio("fin", partida));
        repartos.add(reparto);
        recompensas.add(recompensa);
    }

    /** Los tipos de anuncio en orden: «accion», «turno», «fin»... */
    List<String> tipos() {
        return anuncios.stream().map(Anuncio::tipo).toList();
    }
}
