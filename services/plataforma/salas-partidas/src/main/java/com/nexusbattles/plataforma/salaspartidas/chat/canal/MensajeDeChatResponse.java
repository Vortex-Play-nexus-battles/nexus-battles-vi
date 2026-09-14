package com.nexusbattles.plataforma.salaspartidas.chat.canal;

import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.LogroCompartido;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Tipo;

import java.time.Instant;
import java.util.UUID;

/** Mensaje mensajeDeChat del contrato AsyncAPI. */
public record MensajeDeChatResponse(
        UUID id,
        String tipo,
        UUID idSala,
        Autor autor,
        String texto,
        LogroCompartido logro,
        Instant enviadoEn) {

    public record Autor(UUID id, String apodo) { }

    public static MensajeDeChatResponse de(MensajeDeChat m) {
        return new MensajeDeChatResponse(
                m.id(),
                m.tipo() == Tipo.LOGRO ? "chat.logro" : "chat.mensaje",
                m.canal().idSala(),
                new Autor(m.autor().id(), m.autor().apodo()),
                m.texto(),
                m.logro(),
                m.enviadoEn());
    }
}
