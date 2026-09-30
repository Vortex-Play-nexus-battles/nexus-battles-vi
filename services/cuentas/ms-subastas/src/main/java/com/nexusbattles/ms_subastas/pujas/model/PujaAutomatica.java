package com.nexusbattles.ms_subastas.pujas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "pujas_automaticas", uniqueConstraints = @UniqueConstraint(columnNames = {"subastaId", "jugadorId"}))
@Data
@NoArgsConstructor
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

    /**
     * Apodo del jugador al configurarla. Las pujas que emite el motor no traen
     * token —las dispara un trabajo programado—, asi que es lo unico con lo que
     * la puja automatica puede decir quien pujo (B8, 7.7.9).
     */
    @Column(length = 60)
    private String apodoJugador;

    public PujaAutomatica(UUID id, UUID subastaId, UUID jugadorId, BigDecimal limite, boolean activa) {
        this.id = id;
        this.subastaId = subastaId;
        this.jugadorId = jugadorId;
        this.limite = limite;
        this.activa = activa;
    }
}
