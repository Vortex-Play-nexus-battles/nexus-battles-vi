package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;
import java.util.UUID;

/** 404: la partida no existe o ya se cerro. */
public class PartidaNoEncontrada extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/partida-no-encontrada");

    public PartidaNoEncontrada(UUID idPartida) {
        super(TIPO,
              "Esa partida no existe",
              404,
              "La partida " + idPartida + " no existe o ya termino.");
    }
}
