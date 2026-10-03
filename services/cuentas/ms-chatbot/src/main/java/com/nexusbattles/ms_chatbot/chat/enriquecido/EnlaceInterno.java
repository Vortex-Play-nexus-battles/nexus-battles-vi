package com.nexusbattles.ms_chatbot.chat.enriquecido;

// ms-chatbot.yaml 1.3.4: EnlaceInterno. `destino` es el id de una vista de la
// matriz de acceso del frontend (comun/matriz-acceso.js), nunca una URL: el
// chatbot no puede mandar a nadie fuera del sitio.
public record EnlaceInterno(String texto, String destino) {
}
