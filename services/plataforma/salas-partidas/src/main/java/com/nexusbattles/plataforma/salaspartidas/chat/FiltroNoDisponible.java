package com.nexusbattles.plataforma.salaspartidas.chat;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/** Sale por la cola privada de quien escribio, en formato problem details (regla 4). */
public class FiltroNoDisponible extends ErrorDeNegocio {

    public static final URI TIPO = URI.create("https://nexusbattles.local/errores/filtro-no-disponible");

    public FiltroNoDisponible() {
        super(TIPO, "No se pudo verificar el mensaje", 503, "El filtro de contenido no respondio y el mensaje no se entrego. Intenta de nuevo en un momento.");
    }
}
