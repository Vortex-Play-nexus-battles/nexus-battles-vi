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

    // NUEVO: distingue si esta fila es una reserva de puja (RESERVA), un débito
    // directo ya cobrado (DEBITO), o un crédito ya otorgado (CREDITO). Sin esto,
    // reversar() no puede saber si el refId que recibe corresponde a un débito
    // real (lo único que debe poder revertir) o a otra cosa — confundirlos crea
    // o destruye saldo incorrectamente (ver el fix en CreditoService.reversar()).
    @Column(name = "tipo_operacion", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private TipoOperacion tipoOperacion;

    @Column(name = "creado", updatable = false)
    private OffsetDateTime creado;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    public enum EstadoReserva {
        ACTIVA, LIBERADA, CONSUMIDA
    }

    public enum TipoOperacion {
        RESERVA, DEBITO, CREDITO
    }

    @PrePersist
    protected void onCreate() {
        if (estado == null) estado = EstadoReserva.ACTIVA;
        creado = OffsetDateTime.now();
    }
}
