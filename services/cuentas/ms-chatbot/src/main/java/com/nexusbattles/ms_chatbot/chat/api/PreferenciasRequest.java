package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.preferencias.NivelDeDetalle;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasDeRespuesta;
import jakarta.validation.constraints.NotNull;

// ms-chatbot.yaml 1.3.6: PUT /chat/preferencias. Los dos campos son
// obligatorios: se guarda el par completo, no un parche.
public record PreferenciasRequest(@NotNull IdiomaPreferido idioma, @NotNull NivelDeDetalle nivelDetalle) {

    public PreferenciasDeRespuesta comoPreferencias() {
        return new PreferenciasDeRespuesta(idioma, nivelDetalle);
    }
}
