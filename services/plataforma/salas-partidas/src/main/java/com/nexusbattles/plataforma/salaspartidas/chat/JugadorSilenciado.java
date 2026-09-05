package com.nexusbattles.plataforma.salaspartidas.chat;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/** Sale por la cola privada de quien escribio, en formato problem details (regla 4). */
public class JugadorSilenciado extends ErrorDeNegocio {

    public static final URI TIPO = URI.create("https://nexusbattles.local/errores/jugador-silenciado");

    public JugadorSilenciado() {
        super(TIPO, "No puedes escribir en el chat", 403, "Tienes una sancion activa de silencio. Mientras dure, tus mensajes no salen al canal.");
    }
}
