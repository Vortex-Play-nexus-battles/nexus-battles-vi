package com.nexusbattles.plataforma.salaspartidas.chat;

/** Entrega en tiempo real a los participantes del canal. */
public interface PublicadorDeChat {

    void publicar(MensajeDeChat mensaje);
}
