package com.nexusbattles.ms_finanzas.creditos.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reserva_credito")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "jugador_uid", nullable = false, length = 64)
    private String jugadorUid;

    @Column(name = "monto", nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(name = "concepto", nullable = false, length = 128)
    private String concepto;

    @Column(name = "referencia_id", nullable = false, length = 128)
    private String referenciaId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "estado", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private EstadoReserva estado;

    @Column(name = "creado", updatable = false)
    private OffsetDateTime creado;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    public enum EstadoReserva {
        ACTIVA, LIBERADA, CONSUMIDA
    }

    @PrePersist
    protected void onCreate() {
        if (estado == null) estado = EstadoReserva.ACTIVA;
        creado = OffsetDateTime.now();
    }
}
