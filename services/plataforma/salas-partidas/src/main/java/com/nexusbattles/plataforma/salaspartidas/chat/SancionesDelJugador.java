package com.nexusbattles.plataforma.salaspartidas.chat;

import java.util.UUID;

/** Sanciones de RF-USR-004. Un jugador silenciado no escribe en el chat. */
public interface SancionesDelJugador {

    boolean estaSilenciado(UUID idJugador);
}
