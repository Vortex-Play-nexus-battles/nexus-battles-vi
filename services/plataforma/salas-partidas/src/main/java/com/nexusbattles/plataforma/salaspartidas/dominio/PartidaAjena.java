package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * Un jugador pide una partida que no juega — 403 {@code partida-ajena}
 * (salas-partidas.yaml 1.7.0).
 *
 * <p>El poder, las cargas y las acciones disponibles de cada heroe son
 * informacion de la partida, no publica: solo la ven quienes la juegan y los
 * roles de operacion que atienden sus reportes.
 */
public class PartidaAjena extends ErrorDeNegocio {

    public static final URI TIPO = URI.create("https://nexusbattles.local/errores/partida-ajena");

    public PartidaAjena() {
        super(TIPO, "No participas en esta partida", 403,
                "Solo quienes juegan una partida pueden ver su estado.");
    }
}
