package com.nexusbattles.ms_subastas.panel.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * «Productos pendientes de recoger» (7.7.9): lo ganado al vencer una subasta,
 * ya pagado y ya del ganador en inventario, pero todavia bajo el bloqueo de la
 * subasta hasta que lo recoge. «Tiempo limite para reclamar (7 dias)».
 *
 * <p>La clave es la subasta: una subasta adjudicada tiene un solo ganador y
 * por tanto como mucho un pendiente. Asi el cierre, que se reintenta cada 30 s
 * si algo falla, no puede crear dos.
 */
@Entity
@Table(name = "pendientes_de_recoger")
@Data
@NoArgsConstructor
public class PendienteDeRecoger implements Persistable<UUID> {

    @Id
    private UUID subastaId;

    @Column(nullable = false)
    private UUID ganadorId;

    @Column(nullable = false)
    private String elementoInventarioId;

    @Column(nullable = false)
    private BigDecimal montoPagado;

    @Column(nullable = false)
    private Instant ganadaEn;

    @Column(nullable = false)
    private Instant venceEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPendiente estado;

    private Instant resueltoEn;

    @Version
    private long version;

    /** Mismo motivo que en {@code Subasta}: el id lo pone la aplicacion. */
    @Transient
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private boolean nueva = true;

    public PendienteDeRecoger(UUID subastaId, UUID ganadorId, String elementoInventarioId, BigDecimal montoPagado,
                              Instant ganadaEn, Instant venceEn) {
        this.subastaId = subastaId;
        this.ganadorId = ganadorId;
        this.elementoInventarioId = elementoInventarioId;
        this.montoPagado = montoPagado;
        this.ganadaEn = ganadaEn;
        this.venceEn = venceEn;
        this.estado = EstadoPendiente.PENDIENTE;
    }

    @PostLoad
    @PostPersist
    void yaEstaEnLaBase() {
        this.nueva = false;
    }

    @Override
    public UUID getId() {
        return subastaId;
    }

    @Override
    public boolean isNew() {
        return nueva;
    }

    public boolean estaPendiente() {
        return estado == EstadoPendiente.PENDIENTE;
    }

    /** Deja constancia de como termino. */
    public void resolver(EstadoPendiente estadoFinal, Instant cuando) {
        if (estadoFinal == EstadoPendiente.PENDIENTE) {
            throw new IllegalArgumentException("resolver un pendiente es sacarlo de PENDIENTE");
        }
        this.estado = estadoFinal;
        this.resueltoEn = cuando;
    }
}
