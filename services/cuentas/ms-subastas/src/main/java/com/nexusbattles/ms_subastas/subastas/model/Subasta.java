package com.nexusbattles.ms_subastas.subastas.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Borrador de la tabla Subastas pendiente del diseno conjunto del Dia 1
 * (Cristian HU-SUB-011, Edwin HU-SUB-001, Andres HU-SUB-004). Edwin es quien
 * crea el dato base; este modelo solo cubre lo que el motor de pujas necesita
 * leer/escribir y puede cambiar cuando se cierre el diseno compartido.
 */
@Entity
@Table(name = "subastas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Subasta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID productoId;

    @Column(nullable = false)
    private UUID vendedorId;

    @Column(nullable = false)
    private BigDecimal ofertaVigente;

    @Column(nullable = false)
    private BigDecimal incrementoMinimo;

    private BigDecimal precioCompraInmediata;

    private UUID mejorPostorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSubasta estado;

    @Column(nullable = false)
    private Instant fechaFin;

    @Version
    private long version;

    public boolean estaActiva() {
        return estado == EstadoSubasta.ACTIVA;
    }

    public boolean esVendedor(UUID jugadorId) {
        return vendedorId.equals(jugadorId);
    }
}
