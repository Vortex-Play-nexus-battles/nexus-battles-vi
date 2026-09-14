package com.nexusbattles.plataforma.salaspartidas.chat;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/** Sale por la cola privada de quien escribio, en formato problem details (regla 4). */
public class ContenidoBloqueado extends ErrorDeNegocio {

    public static final URI TIPO = URI.create("https://nexusbattles.local/errores/contenido-bloqueado");

    public ContenidoBloqueado() {
        super(TIPO, "Mensaje bloqueado", 422, "El mensaje contiene terminos que no estan permitidos y no se entrego al canal.");
    }
}
