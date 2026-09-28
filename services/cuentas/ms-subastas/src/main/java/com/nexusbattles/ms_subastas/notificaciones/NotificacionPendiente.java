package com.nexusbattles.ms_subastas.notificaciones;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila del outbox transaccional. Se escribe en la misma transaccion que el
 * cambio de negocio que la motiva, asi que un aviso no puede perderse porque
 * el proceso se caiga justo despues de cerrar una subasta.
 *
 * <p>Desde B8 tiene dos salidas independientes: la bandeja
 * ({@code enviadaEn}) y, si el tipo lo pide, el correo ({@code correoEstado}).
 * Un fallo en una no retiene a la otra.
 *
 * <p><b>El id no es aleatorio:</b> lo deriva {@link NotificacionOutbox} de la
 * clave del evento. Es lo que hace idempotente encolar (el mismo hecho dos
 * veces es un solo aviso), estable el identificador que recibe
 * notificaciones y estable la {@code Idempotency-Key} del correo.
 */
@Entity
@Table(name = "notificaciones_pendientes")
@Data
@NoArgsConstructor
public class NotificacionPendiente {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoNotificacion tipo;

    @Column(nullable = false)
    private UUID destinatarioId;

    @Column(nullable = false)
    private UUID subastaId;

    /** Titulo con el producto; nulo en filas anteriores a B8, que usan el del tipo. */
    private String titulo;

    private String detalle;

    @Column(nullable = false)
    private Instant creadaEn;

    /** Null mientras el aviso no se haya entregado al servicio de notificaciones. */
    private Instant enviadaEn;

    /** La bandeja lo rechazo por algo que reintentar no arregla (4xx distinto de 409). */
    private Instant fallidaEn;

    @Column(nullable = false)
    private boolean conCorreo;

    private String asunto;

    @Enumerated(EnumType.STRING)
    private EstadoCorreo correoEstado;

    @Column(nullable = false)
    private int correoIntentos;

    private Instant correoProximoIntentoEn;

    private Instant correoEnviadoEn;

    public NotificacionPendiente(UUID id, TipoNotificacion tipo, UUID destinatarioId, UUID subastaId,
                                 String detalle, Instant creadaEn, Instant enviadaEn) {
        this.id = id;
        this.tipo = tipo;
        this.destinatarioId = destinatarioId;
        this.subastaId = subastaId;
        this.detalle = detalle;
        this.creadaEn = creadaEn;
        this.enviadaEn = enviadaEn;
    }

    /** El titulo que vera el jugador: el guardado, o el del tipo en filas antiguas. */
    public String tituloVisible() {
        return titulo == null || titulo.isBlank() ? tipo.getTitulo() : titulo;
    }
}
