package com.nexusbattles.ms_chatbot.chat.moderacion;

// B11 — 7.4.8: «filtrado de contenido inapropiado en mensajes del usuario».
// El texto del usuario pasa por la lista negra de la plataforma
// (moderacion-lista-negra.yaml 2.0.0) antes de que el chatbot lo procese o lo
// guarde.
public interface ModeracionDeContenido {

    // No devuelve nada si el texto se puede procesar.
    //
    // Lanza ContenidoBloqueado si coincide con un termino prohibido, y
    // ModeracionNoDisponible si la lista negra no responde y la politica es
    // no dejar pasar lo que no se pudo verificar.
    void verificar(String texto);
}
