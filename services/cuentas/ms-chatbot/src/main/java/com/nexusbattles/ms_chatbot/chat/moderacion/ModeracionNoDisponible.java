package com.nexusbattles.ms_chatbot.chat.moderacion;

// La lista negra no respondio y no se puede verificar el mensaje (503, motivo
// MODERACION_NO_DISPONIBLE). Fail-closed por omision, igual que el chat de
// salas, comentarios y torneos (D-14).
public class ModeracionNoDisponible extends RuntimeException {

    public ModeracionNoDisponible() {
        super("No pudimos revisar tu mensaje en este momento. Intenta de nuevo en unos segundos.");
    }
}
