package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CanalDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Doble del canal que anota lo anunciado, para probar el caso de uso sin
 * levantar un servidor WebSocket.
 *
 * <p>No es un mock: guarda lo que recibe y deja consultarlo. Lo que importa en
 * {@code IngresarASalaTest} no es que se llame al canal, sino <b>cuando</b> —
 * despues de guardar, y nunca tras un rechazo.
 */
class CanalDeSalaEspia implements CanalDeSala {

    record Anuncio(UUID idSala, UUID idJugador, int ocupacion) {
    }

    private final List<Anuncio> anuncios = new ArrayList<>();

    @Override
    public void anunciarIngreso(Sala sala, UUID idJugador) {
        anuncios.add(new Anuncio(sala.id(), idJugador, sala.ocupacion()));
    }

    List<Anuncio> anuncios() {
        return List.copyOf(anuncios);
    }

    boolean noAnuncioNada() {
        return anuncios.isEmpty();
    }
}
