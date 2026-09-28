package com.nexusbattles.ms_chatbot.chat.identidad;

import java.util.Objects;
import java.util.UUID;

// B11: quien habla con el chatbot, ya resuelto y verificado.
//
//   * Usuario: su uid sale SOLO del token (ADR-002). La clave de su
//     conversacion es el uid, como antes de B11, asi que no pierde historial.
//   * Visitante: la sesion que emitio el servidor. La clave de su
//     conversacion es 'anonimo:<id de la sesion>': otro espacio de claves, que
//     ningun uid puede ocupar (lo garantiza ademas una restriccion en la base,
//     V5). Asi X-Id-Sesion-Anonima jamas abre la conversacion de un usuario.
//
// tokenCrudo solo existe para el usuario: se reenvia tal cual a inventario,
// subastas, notificaciones y finanzas (ADR-001/ADR-005); el chatbot nunca
// fabrica ni usa una credencial de servicio para leer datos de un jugador.
public record IdentidadDelChat(boolean autenticado, String claveDeConversacion, String uid, String tokenCrudo,
                               SesionAnonima sesion) {

    static final String PREFIJO_VISITANTE = "anonimo:";

    public IdentidadDelChat {
        Objects.requireNonNull(claveDeConversacion);
    }

    public static IdentidadDelChat usuario(UUID uid, String tokenCrudo) {
        return new IdentidadDelChat(true, uid.toString(), uid.toString(), tokenCrudo, null);
    }

    public static IdentidadDelChat visitante(SesionAnonima sesion) {
        return new IdentidadDelChat(false, sesion.claveDeConversacion(), null, null, sesion);
    }

    // La clave del limite de frecuencia: por usuario o por sesion, en espacios
    // distintos para que un visitante no agote el cupo de un usuario.
    public String claveDeLimite() {
        return (autenticado ? "usuario:" : "sesion:") + claveDeConversacion;
    }
}
