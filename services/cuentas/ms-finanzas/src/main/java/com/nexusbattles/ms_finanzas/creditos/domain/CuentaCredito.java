package com.nexusbattles.ms_finanzas.creditos.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
// El esquema NO se repite aqui: lo fija spring.jpa.properties.hibernate.default_schema
// en application.properties, igual que hace ReservaCredito. Asi renombrarlo es
// un cambio de configuracion y no una cacería por el codigo.
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
        BigDecimal bruto = (saldoBruto != null) ? saldoBruto : BigDecimal.ZERO;
        BigDecimal reservado = (saldoReservado != null) ? saldoReservado : BigDecimal.ZERO;
        BigDecimal disponible = bruto.subtract(reservado);
        return disponible.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : disponible;
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
