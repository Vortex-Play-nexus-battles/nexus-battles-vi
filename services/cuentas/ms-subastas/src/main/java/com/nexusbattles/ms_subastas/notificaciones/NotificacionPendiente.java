package com.nexusbattles.ms_subastas.notificaciones;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila del outbox transaccional. Se escribe en la misma transaccion que el
 * cambio de negocio que la motiva, asi que un aviso no puede perderse porque
 * el proceso se caiga justo despues de cerrar una subasta.
 */
@Entity
@Table(name = "notificaciones_pendientes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionPendiente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoNotificacion tipo;

    @Column(nullable = false)
    private UUID destinatarioId;

    @Column(nullable = false)
    private UUID subastaId;

    private String detalle;

    @Column(nullable = false)
    private Instant creadaEn;

    /** Null mientras el aviso no se haya entregado al servicio de notificaciones. */
    private Instant enviadaEn;
}
