package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * Alguien intento jugar cuando no le tocaba — RF-JUE-017.
 *
 * <p>Lo dice el contrato del canal: «El servidor valida que sea el turno de
 * quien envia; si no lo es, responde por la cola privada con un error en
 * formato problem details». Este es ese error.
 *
 * <p><b>409 y no 403.</b> El jugador tiene derecho a jugar en esta partida —es
 * suya— solo que todavia no le toca. Es un problema de estado, y se arregla
 * esperando, no pidiendo permiso.
 *
 * <p>El mensaje <b>no nombra a quien le toca</b>. La vista ya lo sabe: el turno
 * en curso viaja por el canal para todos. Repetirlo aqui seria una via de
 * enumerar participantes a base de mandar acciones a destiempo.
 */
public class NoEsTuTurno extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/no-es-tu-turno");

    public NoEsTuTurno() {
        super(TIPO,
              "Todavia no es tu turno",
              409,
              "Espera a que te toque para jugar tu accion.");
    }
}
