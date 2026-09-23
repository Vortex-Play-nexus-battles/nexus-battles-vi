package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Un aviso al modulo de notificaciones que todavia no se ha entregado (o ya
 * si) — HU-NOT-005, CA-04: si el canal no responde se registra el intento y
 * se reintenta; nunca se pierde en silencio.
 *
 * <p>El {@code id} es el identificador del evento y viaja al modulo de
 * notificaciones, que responde 409 si ya lo tenia: por eso un reintento no
 * duplica el aviso en la bandeja.
 */
@Entity
@Table(name = "avisos_pendientes")
public class AvisoPendiente {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, length = 2000)
    private String cuerpo;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "entregado_en")
    private OffsetDateTime entregadoEn;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    protected AvisoPendiente() {
    }

    public AvisoPendiente(UUID id, UUID usuarioId, String tipo, String titulo, String cuerpo, OffsetDateTime creadoEn) {
        this.id = Objects.requireNonNull(id);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.tipo = Objects.requireNonNull(tipo);
        this.titulo = Objects.requireNonNull(titulo);
        this.cuerpo = Objects.requireNonNull(cuerpo);
        this.creadoEn = Objects.requireNonNull(creadoEn);
    }

    public void entregado(OffsetDateTime ahora) {
        this.entregadoEn = ahora;
        this.intentos++;
        this.ultimoError = null;
    }

    public void fallo(String motivo, OffsetDateTime ahora) {
        this.intentos++;
        this.ultimoError = motivo == null ? "" : motivo.substring(0, Math.min(motivo.length(), 500));
    }

    public UUID id() {
        return id;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public String tipo() {
        return tipo;
    }

    public String titulo() {
        return titulo;
    }

    public String cuerpo() {
        return cuerpo;
    }

    public OffsetDateTime creadoEn() {
        return creadoEn;
    }

    public OffsetDateTime entregadoEn() {
        return entregadoEn;
    }

    public int intentos() {
        return intentos;
    }

    public String ultimoError() {
        return ultimoError;
    }
}
