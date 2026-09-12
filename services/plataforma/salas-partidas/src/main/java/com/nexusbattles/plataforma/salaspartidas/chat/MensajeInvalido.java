package com.nexusbattles.plataforma.salaspartidas.chat;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/** Sale por la cola privada de quien escribio, en formato problem details (regla 4). */
public class MensajeInvalido extends ErrorDeNegocio {

    public static final URI TIPO = URI.create("https://nexusbattles.local/errores/mensaje-invalido");

    public MensajeInvalido(String detalle) {
        super(TIPO, "Revisa el mensaje", 400, detalle);
    }
}
