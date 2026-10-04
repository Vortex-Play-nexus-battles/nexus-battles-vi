package com.nexusbattles.ms_subastas.panel.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Una subasta en la «Lista de seguimiento» de un jugador (7.7.9): «Productos
 * marcados para observacion», con avisos cuando cambian y el recordatorio de
 * 1 hora antes del cierre (7.7.8).
 */
@Entity
@Table(name = "seguimientos")
@IdClass(Seguimiento.Clave.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Seguimiento {

    @Id
    @Column(nullable = false)
    private UUID subastaId;

    @Id
    @Column(nullable = false)
    private UUID jugadorId;

    @Column(nullable = false)
    private Instant creadoEn;

    /** Clave compuesta: un jugador sigue una subasta una sola vez. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Clave implements Serializable {
        private UUID subastaId;
        private UUID jugadorId;
    }
}
