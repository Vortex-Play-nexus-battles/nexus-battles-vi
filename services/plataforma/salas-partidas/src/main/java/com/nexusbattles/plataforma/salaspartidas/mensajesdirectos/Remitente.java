package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.util.Objects;
import java.util.UUID;

/**
 * Quien escribe un mensaje privado, tal como lo identifica su token.
 *
 * <p>Nunca sale del cuerpo del mensaje ni del destino STOMP: lo construyen
 * los adaptadores de entrada a partir del principal del CONNECT (STOMP) o del
 * JWT de la peticion (REST). Asi no hay forma de escribir en nombre de otro.
 *
 * @param id    el {@code uid} estable (ADR-002)
 * @param apodo el nombre visible, para pintarlo sin preguntar a nadie
 */
public record Remitente(UUID id, String apodo) {

    public Remitente {
        Objects.requireNonNull(id, "Un mensaje privado sin remitente no se puede atribuir.");
        if (apodo == null || apodo.isBlank()) {
            apodo = "Jugador";
        }
    }
}
