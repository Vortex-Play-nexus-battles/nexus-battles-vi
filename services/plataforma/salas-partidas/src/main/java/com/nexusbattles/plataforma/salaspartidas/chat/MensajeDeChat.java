package com.nexusbattles.plataforma.salaspartidas.chat;

import java.time.Instant;
import java.util.UUID;

/**
 * Un mensaje ya aceptado: paso el filtro, su autor no esta silenciado y
 * quedo en el historial. Lo que no llega hasta aqui no existe para el canal.
 */
public record MensajeDeChat(
        UUID id,
        Canal canal,
        Autor autor,
        Tipo tipo,
        String texto,
        LogroCompartido logro,
        Instant enviadoEn) {

    public enum Tipo { MENSAJE, LOGRO }

    /** Quien escribe, tal como lo identifica el token. */
    public record Autor(UUID id, String apodo) { }

    /** Detalle del logro de mision que se comparte en el chat (CA-02). */
    public record LogroCompartido(String mision, String titulo) { }
}
