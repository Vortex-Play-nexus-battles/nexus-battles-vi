package com.nexusbattles.ms_chatbot.chat.preferencias;

import java.util.Objects;

// ms-chatbot.yaml 1.3.6: como quiere quien pregunta que se le responda. Con
// POR_DEFECTO el asistente responde exactamente como hasta 1.3.5.
//
// 1.15.0: SIN_ELEGIR es lo que tiene quien nunca guardo preferencias (idioma
// y nivel de detalle automaticos); el nivel AUTOMATICO se traduce antes de
// responder (ExperienciaDelChat), asi que el motor recibe siempre uno de los
// otros tres.
public record PreferenciasDeRespuesta(IdiomaPreferido idioma, NivelDeDetalle nivelDetalle) {

    /** Como responde el motor sin preferencias: el idioma de la pregunta y la respuesta completa. */
    public static final PreferenciasDeRespuesta POR_DEFECTO =
        new PreferenciasDeRespuesta(IdiomaPreferido.AUTOMATICO, NivelDeDetalle.NORMAL);

    /** 1.15.0 — las de quien nunca eligio: idioma y nivel de detalle automaticos. */
    public static final PreferenciasDeRespuesta SIN_ELEGIR =
        new PreferenciasDeRespuesta(IdiomaPreferido.AUTOMATICO, NivelDeDetalle.AUTOMATICO);

    public PreferenciasDeRespuesta {
        Objects.requireNonNull(idioma);
        Objects.requireNonNull(nivelDetalle);
    }

    public boolean sonPorDefecto() {
        return equals(POR_DEFECTO);
    }

    /** Las mismas, con otro nivel de detalle. */
    public PreferenciasDeRespuesta conNivel(NivelDeDetalle nivel) {
        return new PreferenciasDeRespuesta(idioma, nivel);
    }
}
