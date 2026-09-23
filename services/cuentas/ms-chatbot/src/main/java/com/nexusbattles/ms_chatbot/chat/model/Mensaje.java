package com.nexusbattles.ms_chatbot.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// HU-CHA-001: un mensaje individual dentro de la ventana de chat.
// 'adjuntoUrl' cubre el criterio "adjuntar capturas" -- el tamano maximo del
// adjunto sigue sin definir (pregunta pendiente al cliente, SRS RF-CHA-002),
// asi que por ahora solo se guarda la URL de lo que ya se subio a otro lado.
@Entity
@Table(name = "mensajes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "conversacion_id", nullable = false)
    private Conversacion conversacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Remitente remitente;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "adjunto_url", length = 500)
    private String adjuntoUrl;

    @Column(name = "fecha_envio", nullable = false)
    private Instant fechaEnvio;

    public Mensaje(Conversacion conversacion, Remitente remitente, String contenido, String adjuntoUrl) {
        this.conversacion = conversacion;
        this.remitente = remitente;
        this.contenido = contenido;
        this.adjuntoUrl = adjuntoUrl;
        this.fechaEnvio = Instant.now();
    }
}
