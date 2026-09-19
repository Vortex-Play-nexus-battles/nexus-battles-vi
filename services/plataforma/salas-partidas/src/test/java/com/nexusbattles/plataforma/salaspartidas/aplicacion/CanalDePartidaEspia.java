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

    @Override
    public void anunciarAccionResuelta(AccionResuelta accion) {
        anuncios.add(new Anuncio("accion", null));
    }

    @Override
    public void anunciarInicio(com.nexusbattles.plataforma.salaspartidas.dominio.Sala sala,
                               Partida partida) {
        anuncios.add(new Anuncio("inicio", partida));
    }

    @Override
    public void anunciarTurno(Partida partida) {
        anuncios.add(new Anuncio("turno", partida));
    }

    @Override
    public void anunciarFin(Partida partida) {
        anuncios.add(new Anuncio("fin", partida));
    }
}
