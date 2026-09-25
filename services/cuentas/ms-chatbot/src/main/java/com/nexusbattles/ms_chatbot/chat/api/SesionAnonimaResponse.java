package com.nexusbattles.ms_chatbot.chat.api;

import java.time.Instant;

// ms-chatbot.yaml 2.0.0: SesionAnonima. El identificador solo viaja aqui y en
// la cabecera de respuesta; en la base queda su huella.
public record SesionAnonimaResponse(String idSesionAnonima, Instant expiraEn) {
}
