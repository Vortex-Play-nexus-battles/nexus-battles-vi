package com.nexusbattles.plataforma.salaspartidas.chat;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;
import java.time.Duration;

/**
 * El autor escribe mas deprisa de lo que admite el chat (429) — auditoria de
 * DEV del 30-sep. Mismo {@code type} que el limite de los mensajes privados
 * ({@code demasiados-mensajes}), para que la interfaz lo pinte igual. Sale por
 * la cola privada de quien escribio, como el resto de errores del chat.
 */
public class DemasiadosMensajes extends ErrorDeNegocio {

    public static final URI TIPO = URI.create("https://nexusbattles.local/errores/demasiados-mensajes");

    public DemasiadosMensajes(Duration espera) {
        super(TIPO, "Vas demasiado rápido", 429, detalle(espera));
    }

    private static String detalle(Duration espera) {
        long segundos = espera == null ? 1 : Math.max(1, (espera.toMillis() + 999) / 1000);
        return "Espera " + segundos + (segundos == 1 ? " segundo" : " segundos")
                + " antes de enviar otro mensaje.";
    }
}
