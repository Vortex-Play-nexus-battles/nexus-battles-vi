package com.nexusbattles.ms_subastas.subastas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Una {@code Idempotency-Key} de publicacion, en la base de datos (B8).
 *
 * <p>Hasta B8 vivia en un mapa en memoria: un reinicio o una segunda replica
 * la olvidaban, y el reintento del cliente volvia a publicar —con otra
 * comision y otro bloqueo de inventario—. La fila se inserta en su propia
 * transaccion antes de cualquier efecto externo, asi que un duplicado
 * concurrente la ve y se rechaza.
 */
@Entity
@Table(name = "publicaciones_idempotentes")
@Data
@NoArgsConstructor
public class PublicacionIdempotente {

    public enum Estado { EN_CURSO, CONFIRMADA, INCIERTA }

    /** uid del vendedor + ":" + la cabecera. */
    @Id
    private String clave;

    @Column(nullable = false)
    private String huella;

    @Column(nullable = false)
    private UUID titular;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    private UUID subastaId;

    /** La respuesta ya serializada, para reproducirla tal cual. */
    private String respuesta;

    @Column(nullable = false)
    private Instant creadaEn;

    @Column(nullable = false)
    private Instant actualizadaEn;
}
