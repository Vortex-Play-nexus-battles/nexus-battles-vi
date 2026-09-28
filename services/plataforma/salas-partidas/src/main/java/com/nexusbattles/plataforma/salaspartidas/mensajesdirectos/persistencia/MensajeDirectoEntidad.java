package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila de mensajes_directos (V13). Solo la usa RepositorioMensajesDirectosJpa. */
@Entity
@Table(name = "mensajes_directos")
class MensajeDirectoEntidad {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String conversacion;

    @Column(name = "id_remitente", nullable = false)
    private UUID idRemitente;

    @Column(name = "apodo_remitente", nullable = false, length = 60)
    private String apodoRemitente;

    @Column(name = "id_destinatario", nullable = false)
    private UUID idDestinatario;

    @Column(name = "apodo_destinatario", length = 60)
    private String apodoDestinatario;

    @Column(nullable = false, length = 500)
    private String texto;

    @Column(name = "enviado_en", nullable = false)
    private Instant enviadoEn;

    @Column(name = "leido_en")
    private Instant leidoEn;

    @Column(name = "id_cliente", length = 64)
    private String idCliente;

    protected MensajeDirectoEntidad() {
    }

    MensajeDirectoEntidad(UUID id, String conversacion, UUID idRemitente, String apodoRemitente,
                          UUID idDestinatario, String apodoDestinatario, String texto, Instant enviadoEn,
                          Instant leidoEn, String idCliente) {
        this.id = id;
        this.conversacion = conversacion;
        this.idRemitente = idRemitente;
        this.apodoRemitente = apodoRemitente;
        this.idDestinatario = idDestinatario;
        this.apodoDestinatario = apodoDestinatario;
        this.texto = texto;
        this.enviadoEn = enviadoEn;
        this.leidoEn = leidoEn;
        this.idCliente = idCliente;
    }

    UUID id() { return id; }
    String conversacion() { return conversacion; }
    UUID idRemitente() { return idRemitente; }
    String apodoRemitente() { return apodoRemitente; }
    UUID idDestinatario() { return idDestinatario; }
    String apodoDestinatario() { return apodoDestinatario; }
    String texto() { return texto; }
    Instant enviadoEn() { return enviadoEn; }
    Instant leidoEn() { return leidoEn; }
    String idCliente() { return idCliente; }
}
