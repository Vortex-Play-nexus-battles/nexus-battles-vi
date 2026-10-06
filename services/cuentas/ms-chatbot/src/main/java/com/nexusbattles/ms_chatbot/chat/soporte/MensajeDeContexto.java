package com.nexusbattles.ms_chatbot.chat.soporte;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// Un mensaje de la conversacion copiado al abrir el ticket (ya redactado).
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class MensajeDeContexto {

    @Column(nullable = false, length = 10)
    private String remitente;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "fecha_envio", nullable = false)
    private Instant fechaEnvio;

    public MensajeDeContexto(String remitente, String contenido, Instant fechaEnvio) {
        this.remitente = remitente;
        this.contenido = contenido;
        this.fechaEnvio = fechaEnvio;
    }
}
