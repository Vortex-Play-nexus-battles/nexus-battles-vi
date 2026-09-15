package com.nexusbattles.ms_finanzas.creditos.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaccion_credito")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransaccionCredito {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ref_id", nullable = false, unique = true, length = 128)
    private String refId;

    @Column(name = "jugador_uid", nullable = false, length = 64)
    private String jugadorUid;

    @Column(name = "monto", nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(name = "concepto", nullable = false, length = 128)
    private String concepto;

    @Column(name = "tipo", nullable = false, length = 32)
    private String tipo;

    @Column(name = "estado", nullable = false, length = 32)
    private String estado;

    @Column(name = "creado", updatable = false)
    private OffsetDateTime creado;

    @PrePersist
    protected void onCreate() {
        creado = OffsetDateTime.now();
    }
}
