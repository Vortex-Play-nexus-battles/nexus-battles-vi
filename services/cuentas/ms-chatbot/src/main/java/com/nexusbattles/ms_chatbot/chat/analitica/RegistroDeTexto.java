package com.nexusbattles.ms_chatbot.chat.analitica;

import java.util.UUID;

// ms-chatbot.yaml 1.3.7: el texto de una pregunta y su conversacion, solo
// para contar palabras clave. Nunca sale del servidor tal cual.
public record RegistroDeTexto(UUID conversacionId, String contenido) {
}
