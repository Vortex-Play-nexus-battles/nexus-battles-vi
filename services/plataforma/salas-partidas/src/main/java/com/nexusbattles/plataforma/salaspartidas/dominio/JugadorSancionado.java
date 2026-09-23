package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * Un jugador con sancion activa intenta entrar a jugar — HU-USR-005 y
 * HU-USR-006, R10.2.
 *
 * <h2>El defecto que cierra</h2>
 *
 * Hasta aqui una suspension o un baneo emitidos por RF-USR-005/006 no
 * impedian jugar. El chat de la sala silenciaba al sancionado, pero podia
 * crear una sala, entrar a cualquier otra y combatir: la sancion era
 * practicamente un mute. Un castigo que solo quita la voz y deja el juego no
 * es la suspension que describe la ficha.
 *
 * <p>403 y no 422: no es que la peticion este mal formada ni que falte un
 * requisito que el jugador pueda arreglar; es que <b>no tiene permitido</b>
 * hacerlo ahora. Mismo criterio que el 403 del chat.
 */
public class JugadorSancionado extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/jugador-sancionado");

    public JugadorSancionado() {
        super(TIPO, "Tienes una sancion activa", 403,
                "Mientras la sancion siga vigente no puedes crear salas ni entrar a batallas."
                        + " Puedes consultarla, y apelarla si crees que es injusta, en «Mis sanciones».");
    }
}
