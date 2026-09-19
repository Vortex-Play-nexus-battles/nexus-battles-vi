package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;
import java.util.UUID;

/** 409: se pidio algo que solo tiene sentido con el combate vivo. */
public class PartidaYaTerminada extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/partida-ya-terminada");

    public PartidaYaTerminada(UUID idPartida) {
        super(TIPO,
              "La partida ya termino",
              409,
              "El combate " + idPartida + " ya finalizo: su turno no avanza mas.");
    }
}
