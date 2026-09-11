package com.nexusbattles.ms_subastas.pujas.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "pujas_automaticas", uniqueConstraints = @UniqueConstraint(columnNames = {"subastaId", "jugadorId"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PujaAutomatica {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID subastaId;

    @Column(nullable = false)
    private UUID jugadorId;

    @Column(nullable = false)
    private BigDecimal limite;

    @Column(nullable = false)
    private boolean activa = true;
}
