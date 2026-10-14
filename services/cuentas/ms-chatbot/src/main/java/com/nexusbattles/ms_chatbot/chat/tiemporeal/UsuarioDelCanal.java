package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import java.security.Principal;
import java.util.Objects;

/**
 * 7.4.9 — quien esta al otro lado de una conexion del canal. Lo fija
 * {@link PoliticaDelCanal} en el CONNECT con las reglas de la API
 * ({@code ResolutorDeIdentidad.nombreEnElCanal}): el {@code uid} de un
 * jugador o {@code anonimo:} + la huella de la sesion de un visitante. Nunca
 * sale de un dato del cliente. Es el nombre por el que Spring resuelve los
 * destinos {@code /user/...}: los avisos de soporte llegan al {@code uid}.
 *
 * @param nombre  uid o {@code anonimo:<huella>}
 * @param jugador si es un jugador con sesion (un visitante no tiene tickets)
 */
public record UsuarioDelCanal(String nombre, boolean jugador) implements Principal {

    public UsuarioDelCanal {
        Objects.requireNonNull(nombre, "nombre");
    }

    @Override
    public String getName() {
        return nombre;
    }
}
