package com.nexusbattles.ms_finanzas.creditos.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cuenta_credito")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaCredito {

    @Id
    @Column(name = "jugador_uid", length = 64)
    private String jugadorUid;

    @Column(name = "saldo_bruto", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoBruto;

    @Column(name = "saldo_reservado", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoReservado;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "creado", updatable = false)
    private OffsetDateTime creado;

    @Column(name = "actualizado")
    private OffsetDateTime actualizado;

    public BigDecimal getSaldoDisponible() {
        if (saldoBruto == null) return BigDecimal.ZERO;
        if (saldoReservado == null) return saldoBruto;
        return saldoBruto.subtract(saldoReservado);
    }

    @PrePersist
    protected void onCreate() {
        if (saldoBruto == null) saldoBruto = BigDecimal.ZERO;
        if (saldoReservado == null) saldoReservado = BigDecimal.ZERO;
        if (version == null) version = 0L;
        creado = OffsetDateTime.now();
        actualizado = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        actualizado = OffsetDateTime.now();
    }
}
