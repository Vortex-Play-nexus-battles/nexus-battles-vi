package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.preferencias.NivelDeDetalle;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasDeRespuesta;

// ms-chatbot.yaml 1.3.6: esquema PreferenciasDelChat.
public record PreferenciasResponse(IdiomaPreferido idioma, NivelDeDetalle nivelDetalle) {

    public static PreferenciasResponse desde(PreferenciasDeRespuesta preferencias) {
        return new PreferenciasResponse(preferencias.idioma(), preferencias.nivelDetalle());
    }
}
