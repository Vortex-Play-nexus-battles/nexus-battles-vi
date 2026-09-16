package com.nexusbattles.ms_subastas.pujas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pujas")
@Data
@NoArgsConstructor
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

    /**
     * Cabecera Idempotency-Key con la que llego la peticion que creo esta puja.
     * Un unico parcial en la base de datos impide que dos pujas compartan
     * clave, de modo que un reintento del cliente encuentra esta misma fila en
     * vez de intentar pujar otra vez.
     *
     * <p>Fuera del constructor a proposito: no forma parte de lo que la puja
     * <em>es</em> —el jugador, el monto, el momento—, sino de como llego. El
     * motor la pone con el setter justo antes de devolverla.
     */
    @Column(length = 128)
    private String idempotencyKey;

    public Puja(UUID id, UUID subastaId, UUID jugadorId, BigDecimal monto, TipoPuja tipo,
                EstadoPuja estado, Instant creadaEn, String reservaCreditoId) {
        this.id = id;
        this.subastaId = subastaId;
        this.jugadorId = jugadorId;
        this.monto = monto;
        this.tipo = tipo;
        this.estado = estado;
        this.creadaEn = creadaEn;
        this.reservaCreditoId = reservaCreditoId;
    }
}
