package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Sesion abierta de un jugador.
 *
 * <p>El identificador es el estable que envia el cliente y que sobrevive a la
 * reconexion, no el de STOMP, que se renueva cada vez. Sin esa distincion la
 * sesion que vuelve de una caida se veria como nueva y recibiria todo lo que
 * tiene sin leer en lugar de solo lo que se perdio.
 */
@Entity
@Table(name = "sesiones_abiertas")
public class RegistroDeSesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fila_id")
    private Long filaId;

    @Column(name = "usuario_id", nullable = false, length = 64)
    private String usuarioId;

    @Column(name = "sesion_id", nullable = false, length = 128)
    private String sesionId;

    @Column(name = "abierta_en", nullable = false)
    private Instant abiertaEn;

    protected RegistroDeSesion() {
    }

    RegistroDeSesion(String usuarioId, String sesionId, Instant abiertaEn) {
        this.usuarioId = usuarioId;
        this.sesionId = sesionId;
        this.abiertaEn = abiertaEn;
    }

    public String getSesionId() {
        return sesionId;
    }
}
