package com.nexusbattles.ms_identidad.auth.segundofactor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un codigo de recuperacion del segundo factor (tabla
 * {@code codigos_recuperacion}, V5): su resumen BCrypt, nunca el valor, y
 * cuando se uso. Se gasta con un UPDATE condicionado
 * ({@link CodigoDeRecuperacionRepository#marcarUsado}): dos entradas
 * simultaneas con el mismo codigo no pasan las dos.
 */
@Entity
@Table(name = "codigos_recuperacion")
public class CodigoDeRecuperacion {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "codigo_hash", nullable = false, length = 100)
    private String codigoHash;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "usado_en")
    private LocalDateTime usadoEn;

    protected CodigoDeRecuperacion() {
    }

    public CodigoDeRecuperacion(Long usuarioId, String codigoHash, LocalDateTime creadoEn) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.codigoHash = codigoHash;
        this.creadoEn = creadoEn;
    }

    public UUID getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getCodigoHash() {
        return codigoHash;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getUsadoEn() {
        return usadoEn;
    }
}
