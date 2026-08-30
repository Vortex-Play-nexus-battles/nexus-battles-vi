package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "terminos_prohibidos")
public class TerminoProhibido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String termino;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    protected TerminoProhibido() {
    }

    public TerminoProhibido(String termino) {
        this.termino = termino;
        this.fechaCreacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTermino() {
        return termino;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void actualizarTermino(String nuevoTermino) {
        this.termino = nuevoTermino;
    }
}
