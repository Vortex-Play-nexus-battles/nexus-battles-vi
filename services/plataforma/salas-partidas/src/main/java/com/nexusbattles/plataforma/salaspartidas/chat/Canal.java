package com.nexusbattles.plataforma.salaspartidas.chat;

import java.util.UUID;

/**
 * Donde ocurre la conversacion: una sala de batalla o la vista general.
 *
 * <p>La historia HU-JUE-015 pide el chat en los dos lugares con propositos
 * distintos, pero las reglas son las mismas, asi que el caso de uso no
 * distingue y solo cambian el destino STOMP y la clave del historial.
 */
public record Canal(UUID idSala) {

    public static Canal deSala(UUID idSala) {
        if (idSala == null) {
            throw new IllegalArgumentException("El canal de una sala necesita su identificador.");
        }
        return new Canal(idSala);
    }

    public static Canal general() {
        return new Canal(null);
    }

    public boolean esGeneral() {
        return idSala == null;
    }

    /** Destino de suscripcion, segun contracts/websocket/salas-partidas.yaml. */
    public String destino() {
        return esGeneral() ? "/tema/chat/general" : "/tema/salas/" + idSala + "/chat";
    }

    /** Clave con la que se guarda el historial. */
    public String clave() {
        return esGeneral() ? "general" : "sala:" + idSala;
    }

    public static Canal desdeClave(String clave) {
        return "general".equals(clave) ? general() : deSala(UUID.fromString(clave.substring("sala:".length())));
    }
}
