package com.nexusbattles.ms_chatbot.chat.preferencias;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// ms-chatbot.yaml 1.3.6 (V8): las preferencias guardadas de una
// conversacion. La clave es la de la conversacion
// (IdentidadDelChat.claveDeConversacion): el uid del jugador, que las
// conserva entre dispositivos, o 'anonimo:<sesion>' para un visitante.
@Entity
@Table(name = "preferencias_chat")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class PreferenciasDelChat {

    @Id
    @Column(length = 255)
    private String clave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdiomaPreferido idioma;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_detalle", nullable = false, length = 20)
    private NivelDeDetalle nivelDetalle;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    public PreferenciasDelChat(String clave, PreferenciasDeRespuesta preferencias, Instant ahora) {
        this.clave = clave;
        cambiar(preferencias, ahora);
    }

    public void cambiar(PreferenciasDeRespuesta preferencias, Instant ahora) {
        this.idioma = preferencias.idioma();
        this.nivelDetalle = preferencias.nivelDetalle();
        this.actualizadoEn = ahora;
    }

    public PreferenciasDeRespuesta comoPreferencias() {
        return new PreferenciasDeRespuesta(idioma, nivelDetalle);
    }
}
