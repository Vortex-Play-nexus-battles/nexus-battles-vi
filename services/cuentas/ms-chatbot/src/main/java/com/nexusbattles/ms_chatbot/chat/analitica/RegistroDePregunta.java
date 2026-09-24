package com.nexusbattles.ms_chatbot.chat.analitica;

import java.time.Instant;
import java.util.UUID;

// HU-CHA-012: una pregunta de usuario, reducida a lo que las analiticas usan.
public record RegistroDePregunta(Instant fechaEnvio, UUID conversacionId) {
}
