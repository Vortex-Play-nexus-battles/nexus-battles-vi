package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;

import java.util.UUID;

/**
 * Mensaje {@code partida.participante.rendido} — canal 1.10.0, revision del
 * modo jugador del 6-oct («Salir» en pleno combate).
 *
 * <p>Va antes del cambio de turno o del fin que provoca la rendicion: los demas
 * ven primero quien se fue (su barra a cero, con su nombre) y despues lo que
 * pasa. Lleva la vida con la que queda, como un afectado de una accion, para
 * que la barra no se calcule en el navegador.
 *
 * @param tipo        discriminador del canal
 * @param idPartida   partida afectada
 * @param idJugador   quien se rindio
 * @param vidaActual  su vida tras rendirse (0)
 * @param vidaMaxima  su vida maxima, para la barra
 */
record AvisoDeRendicion(String tipo, UUID idPartida, UUID idJugador, int vidaActual, int vidaMaxima) {

    static final String TIPO = "partida.participante.rendido";

    static AvisoDeRendicion de(Partida partida, UUID idJugador) {
        ParticipanteDePartida quien = partida.participante(idJugador).orElse(null);
        int vida = quien == null || quien.heroe() == null ? 0 : quien.heroe().vidaActual();
        int maxima = quien == null || quien.heroe() == null ? 0 : quien.heroe().vidaMaxima();
        return new AvisoDeRendicion(TIPO, partida.id(), idJugador, vida, maxima);
    }
}
