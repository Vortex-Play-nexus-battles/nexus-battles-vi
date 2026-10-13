package com.nexusbattles.ms_chatbot.chat.preferencias;

import java.util.Objects;

// ms-chatbot.yaml 1.3.6: como quiere quien pregunta que se le responda. Con
// POR_DEFECTO el asistente responde exactamente como hasta 1.3.5.
public record PreferenciasDeRespuesta(IdiomaPreferido idioma, NivelDeDetalle nivelDetalle) {

    public static final PreferenciasDeRespuesta POR_DEFECTO =
        new PreferenciasDeRespuesta(IdiomaPreferido.AUTOMATICO, NivelDeDetalle.NORMAL);

    public PreferenciasDeRespuesta {
        Objects.requireNonNull(idioma);
        Objects.requireNonNull(nivelDetalle);
    }

    public boolean sonPorDefecto() {
        return equals(POR_DEFECTO);
    }
}
