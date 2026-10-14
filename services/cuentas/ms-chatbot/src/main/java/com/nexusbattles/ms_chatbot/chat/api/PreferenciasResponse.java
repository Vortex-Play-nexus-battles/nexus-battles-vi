package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.preferencias.NivelDeDetalle;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasDeRespuesta;

// ms-chatbot.yaml 1.3.6: esquema PreferenciasDelChat.
//
// 1.15.0 (7.4.5, D-CHA-13): `nivelDetalleEfectivo`, el nivel con el que se le
// responde hoy: el elegido o, con AUTOMATICO, el de su experiencia. Nunca es
// AUTOMATICO.
public record PreferenciasResponse(IdiomaPreferido idioma, NivelDeDetalle nivelDetalle,
                                   NivelDeDetalle nivelDetalleEfectivo) {

    public static PreferenciasResponse desde(PreferenciasDeRespuesta guardadas, PreferenciasDeRespuesta efectivas) {
        return new PreferenciasResponse(guardadas.idioma(), guardadas.nivelDetalle(), efectivas.nivelDetalle());
    }
}
