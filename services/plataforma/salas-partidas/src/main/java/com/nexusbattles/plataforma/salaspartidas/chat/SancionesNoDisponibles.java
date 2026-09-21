package com.nexusbattles.plataforma.salaspartidas.chat;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * Sale por la cola privada de quien escribio, en formato problem details (regla 4).
 *
 * <p>Mismo criterio que {@link FiltroNoDisponible}: si no se puede comprobar
 * la sancion, el mensaje no sale. Dejarlo pasar seria darle voz a quien un
 * moderador silencio, justo cuando el servicio que lo sabe no contesta.
 */
public class SancionesNoDisponibles extends ErrorDeNegocio {

    public static final URI TIPO = URI.create("https://nexusbattles.local/errores/sanciones-no-disponibles");

    public SancionesNoDisponibles() {
        super(TIPO, "No se pudo comprobar tu estado en el chat", 503,
                "El servicio de sanciones no respondio y el mensaje no se entrego. Intenta de nuevo en un momento.");
    }
}
