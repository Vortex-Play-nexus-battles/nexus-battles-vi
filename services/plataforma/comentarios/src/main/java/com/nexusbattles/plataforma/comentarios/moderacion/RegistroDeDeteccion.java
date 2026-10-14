package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.nexusbattles.plataforma.comentarios.DeteccionAutomatica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Por que el filtro automatico retuvo un comentario — HU-COM-007 CA-01, la
 * tabla de V7.
 *
 * <p>Una fila por comentario retenido, con el comentario como clave. Se
 * escribe al publicarlo, en la misma transaccion que el comentario, y la cola
 * y el detalle de moderacion la leen. Nunca guarda el texto del comentario ni
 * los terminos coincidentes: las reglas van por su id.
 */
@Entity
@Table(name = "comentario_detecciones")
public class RegistroDeDeteccion {

    @Id
    @Column(name = "comentario_id", length = 36)
    private String comentarioId;

    @Column(nullable = false)
    private Instant fecha;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private Long[] reglas;

    @Column(length = DeteccionAutomatica.CATEGORIA_MAXIMA)
    private String categoria;

    @Column(length = DeteccionAutomatica.MOTIVO_MAXIMO)
    private String motivo;

    @Column(name = "servicio_no_disponible", nullable = false)
    private boolean servicioNoDisponible;

    protected RegistroDeDeteccion() {
    }

    public RegistroDeDeteccion(String comentarioId, DeteccionAutomatica deteccion) {
        this.comentarioId = comentarioId;
        this.fecha = deteccion.fecha();
        this.reglas = deteccion.reglas().toArray(Long[]::new);
        this.categoria = deteccion.categoria();
        this.motivo = deteccion.motivo();
        this.servicioNoDisponible = deteccion.servicioNoDisponible();
    }

    public String comentarioId() {
        return comentarioId;
    }

    public DeteccionAutomatica aDominio() {
        List<Long> ids = reglas == null ? List.of() : Arrays.asList(reglas);
        return new DeteccionAutomatica(fecha, ids, categoria, motivo, servicioNoDisponible);
    }
}
