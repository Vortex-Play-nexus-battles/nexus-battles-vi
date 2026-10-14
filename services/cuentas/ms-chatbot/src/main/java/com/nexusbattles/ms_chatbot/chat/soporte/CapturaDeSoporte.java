package com.nexusbattles.ms_chatbot.chat.soporte;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * ms-chatbot.yaml 1.14.0 (§7.4.6): una captura de pantalla de una solicitud de
 * soporte (V19), ya examinada y sin metadatos (ExaminadorDeCapturas). El
 * contenido solo se lee al servirla: las listas usan {@link Resumen}.
 */
@Entity
@Table(name = "tickets_soporte_capturas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigido por JPA
public class CapturaDeSoporte {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false)
    private int tamano;

    @Column(nullable = false)
    private int ancho;

    @Column(nullable = false)
    private int alto;

    @Column(nullable = false)
    private byte[] contenido;

    @Column(name = "subida_en", nullable = false)
    private Instant subidaEn;

    public CapturaDeSoporte(UUID ticketId, ExaminadorDeCapturas.CapturaExaminada examinada, Instant ahora) {
        this.ticketId = ticketId;
        this.tipo = examinada.formato().tipoMime();
        this.contenido = examinada.contenido();
        this.tamano = examinada.contenido().length;
        this.ancho = examinada.ancho();
        this.alto = examinada.alto();
        this.subidaEn = ahora;
    }

    /** Lo que se ensena de una captura en una lista, sin su contenido. */
    public interface Resumen {
        UUID getId();

        UUID getTicketId();

        String getTipo();

        int getTamano();

        int getAncho();

        int getAlto();

        Instant getSubidaEn();
    }
}
