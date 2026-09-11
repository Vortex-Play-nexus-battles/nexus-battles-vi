package com.nexusbattles.ms_subastas.pujas.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pujas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Puja {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID subastaId;

    @Column(nullable = false)
    private UUID jugadorId;

    @Column(nullable = false)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPuja tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPuja estado;

    @Column(nullable = false)
    private Instant creadaEn;

    /** Referencia a la reserva de creditos en ms-finanzas (no se lee ni se escribe su saldo aqui). */
    private String reservaCreditoId;
}
