package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un reporte: una persona marcando un comentario — RF-COM-006.
 *
 * <p>El reportante es el {@code uid} del token de quien manda la peticion,
 * nunca un campo del cuerpo. La unicidad (comentario, reportante) la garantiza
 * el indice de V4, no solo el servicio: dos peticiones simultaneas del mismo
 * usuario cargan cada una un estado que no ve a la otra.
 */
@Entity
@Table(name = "comentario_reportes")
public class RegistroDeReporte {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "comentario_id", nullable = false, length = 36)
    private String comentarioId;

    @Column(name = "reportante_id", nullable = false, length = 64)
    private String reportanteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CategoriaDeReporte categoria;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    private Instant fecha;

    protected RegistroDeReporte() {
    }

    public RegistroDeReporte(String id, String comentarioId, String reportanteId,
            CategoriaDeReporte categoria, String descripcion, Instant fecha) {
        this.id = id;
        this.comentarioId = comentarioId;
        this.reportanteId = reportanteId;
        this.categoria = categoria;
        this.descripcion = descripcion;
        this.fecha = fecha;
    }

    public String id() {
        return id;
    }

    public String comentarioId() {
        return comentarioId;
    }

    public String reportanteId() {
        return reportanteId;
    }

    public CategoriaDeReporte categoria() {
        return categoria;
    }

    public String descripcion() {
        return descripcion;
    }

    public Instant fecha() {
        return fecha;
    }
}
