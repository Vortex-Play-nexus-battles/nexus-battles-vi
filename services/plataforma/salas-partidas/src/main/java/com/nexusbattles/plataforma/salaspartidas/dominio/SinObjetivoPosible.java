package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * La accion no tiene a quien apuntar — RF-JUE-017.
 *
 * <p>Pasa en dos casos: no se indico objetivo y hay mas de un rival en pie —con
 * tres o mas participantes elegir por el jugador seria decidir su jugada—, o el
 * objetivo indicado no esta en la partida o ya cayo.
 *
 * <p>Con un solo rival vivo SI se resuelve solo: en un 1v1 no hay ambiguedad
 * que resolver y obligar a mandar el identificador seria burocracia.
 */
public class SinObjetivoPosible extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/sin-objetivo-posible");

    public SinObjetivoPosible(String detalle) {
        super(TIPO, "No hay a quien atacar", 409, detalle);
    }
}
