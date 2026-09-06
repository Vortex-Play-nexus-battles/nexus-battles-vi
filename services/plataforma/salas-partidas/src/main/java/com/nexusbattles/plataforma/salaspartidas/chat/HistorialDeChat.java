package com.nexusbattles.plataforma.salaspartidas.chat;

import java.util.List;

/** Historial de la sesion: lo que se escribio queda para quien entra despues. */
public interface HistorialDeChat {

    void guardar(MensajeDeChat mensaje);

    /** Los ultimos mensajes del canal, del mas antiguo al mas reciente. */
    List<MensajeDeChat> ultimos(Canal canal, int cantidad);
}
