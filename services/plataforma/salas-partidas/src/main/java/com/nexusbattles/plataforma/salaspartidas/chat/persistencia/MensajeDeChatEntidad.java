package com.nexusbattles.plataforma.salaspartidas.chat.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila de mensajes_de_chat (V2). Solo la usa HistorialDeChatJpa. */
@Entity
@Table(name = "mensajes_de_chat")
class MensajeDeChatEntidad {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String canal;

    @Column(name = "id_autor", nullable = false)
    private UUID idAutor;

    @Column(name = "apodo_autor", nullable = false, length = 60)
    private String apodoAutor;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 500)
    private String texto;

    @Column(name = "logro_mision", length = 120)
    private String logroMision;

    @Column(name = "logro_titulo", length = 120)
    private String logroTitulo;

    @Column(name = "enviado_en", nullable = false)
    private Instant enviadoEn;

    protected MensajeDeChatEntidad() {
    }

    MensajeDeChatEntidad(UUID id, String canal, UUID idAutor, String apodoAutor, String tipo,
            String texto, String logroMision, String logroTitulo, Instant enviadoEn) {
        this.id = id;
        this.canal = canal;
        this.idAutor = idAutor;
        this.apodoAutor = apodoAutor;
        this.tipo = tipo;
        this.texto = texto;
        this.logroMision = logroMision;
        this.logroTitulo = logroTitulo;
        this.enviadoEn = enviadoEn;
    }

    UUID id() { return id; }
    String canal() { return canal; }
    UUID idAutor() { return idAutor; }
    String apodoAutor() { return apodoAutor; }
    String tipo() { return tipo; }
    String texto() { return texto; }
    String logroMision() { return logroMision; }
    String logroTitulo() { return logroTitulo; }
    Instant enviadoEn() { return enviadoEn; }
}
