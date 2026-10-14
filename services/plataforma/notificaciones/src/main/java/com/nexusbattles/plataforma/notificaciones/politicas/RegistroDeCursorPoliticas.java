package com.nexusbattles.plataforma.notificaciones.politicas;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Hasta donde vio cada jugador los cambios en las politicas de uso — SA-11,
 * tabla {@code cursor_cambios_politica} (V4). Igual que el cursor del
 * catalogo: {@code hasta} es la marca del ultimo lote incorporado y
 * {@code consultadoEn} cuando se pregunto por ultima vez (intervalo minimo).
 * Se escribe con las sentencias de {@link CursorPoliticasRepository}; esta
 * clase solo se lee.
 */
@Entity
@Table(name = "cursor_cambios_politica")
public class RegistroDeCursorPoliticas {

    @Id
    @Column(name = "usuario_id", nullable = false, length = 64)
    private String usuarioId;

    @Column(name = "hasta", nullable = false)
    private Instant hasta;

    @Column(name = "consultado_en", nullable = false)
    private Instant consultadoEn;

    protected RegistroDeCursorPoliticas() {
    }

    public RegistroDeCursorPoliticas(String usuarioId, Instant hasta, Instant consultadoEn) {
        this.usuarioId = usuarioId;
        this.hasta = hasta;
        this.consultadoEn = consultadoEn;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public Instant getHasta() {
        return hasta;
    }

    public Instant getConsultadoEn() {
        return consultadoEn;
    }
}
