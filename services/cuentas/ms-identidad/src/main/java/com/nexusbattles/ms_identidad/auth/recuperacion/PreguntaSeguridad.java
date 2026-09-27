package com.nexusbattles.ms_identidad.auth.recuperacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una pregunta de seguridad de una cuenta (7.1.1): el texto lo escribe la
 * persona —el sistema no inventa un catalogo— y la respuesta se guarda
 * normalizada y con BCrypt ({@link NormalizadorDeRespuestas}), nunca en claro.
 */
@Entity
@Table(name = "preguntas_seguridad")
public class PreguntaSeguridad {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 200)
    private String texto;

    @Column(name = "respuesta_hash", nullable = false, length = 100)
    private String respuestaHash;

    @Column(nullable = false)
    private int orden;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    protected PreguntaSeguridad() {
    }

    public PreguntaSeguridad(Long usuarioId, String texto, String respuestaHash, int orden, LocalDateTime creadoEn) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.texto = texto;
        this.respuestaHash = respuestaHash;
        this.orden = orden;
        this.creadoEn = creadoEn;
    }

    public UUID getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getTexto() {
        return texto;
    }

    public String getRespuestaHash() {
        return respuestaHash;
    }

    public int getOrden() {
        return orden;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
