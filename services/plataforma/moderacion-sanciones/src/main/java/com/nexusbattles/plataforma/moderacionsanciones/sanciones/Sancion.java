package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Una sancion del historial disciplinario — HU-USR-004/005/006.
 *
 * <p>Tres tipos, con efectos distintos: la <b>advertencia</b> queda en el
 * historial y se notifica, pero no restringe nada (CA-02 de HU-USR-004); la
 * <b>suspension</b> tiene fecha fin y mientras dura la cuenta cuenta como
 * sancionada; el <b>baneo</b> no vence. Ninguna se borra: revertir o reducir
 * es escribir sobre la misma fila (CA-04 de HU-USR-006).
 */
@Entity
@Table(name = "sanciones")
public class Sancion {

    /** Tipo de sancion, con su efecto. */
    public enum Tipo {
        /** Queda en el historial y se notifica; no restringe el acceso. */
        ADVERTENCIA,
        /** Restringe hasta {@code vigenteHasta}. */
        SUSPENSION,
        /** Restringe sin fecha fin; solo lo revierte una apelacion favorable. */
        BANEO
    }

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @Column(nullable = false, length = 1000)
    private String motivo;

    @Column(length = 200)
    private String politica;

    @Column(name = "comentario_id", length = 64)
    private String comentarioId;

    @Column(name = "emitida_por", nullable = false)
    private UUID emitidaPor;

    @Column(name = "rol_emisor", nullable = false, length = 30)
    private String rolEmisor;

    @Column(name = "emitida_en", nullable = false)
    private OffsetDateTime emitidaEn;

    @Column(name = "vigente_hasta")
    private OffsetDateTime vigenteHasta;

    @Column(name = "revertida_en")
    private OffsetDateTime revertidaEn;

    @Column(name = "revertida_por")
    private UUID revertidaPor;

    @Column(name = "motivo_reversion", length = 1000)
    private String motivoReversion;

    /** Exigido por JPA. */
    protected Sancion() {
    }

    public Sancion(UUID id, UUID usuarioId, Tipo tipo, String motivo, String politica, String comentarioId,
                   UUID emitidaPor, String rolEmisor, OffsetDateTime emitidaEn, OffsetDateTime vigenteHasta) {
        this.id = Objects.requireNonNull(id);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.tipo = Objects.requireNonNull(tipo);
        this.motivo = Objects.requireNonNull(motivo);
        this.politica = politica;
        this.comentarioId = comentarioId;
        this.emitidaPor = Objects.requireNonNull(emitidaPor);
        this.rolEmisor = Objects.requireNonNull(rolEmisor);
        this.emitidaEn = Objects.requireNonNull(emitidaEn);
        this.vigenteHasta = vigenteHasta;
        if ((tipo == Tipo.SUSPENSION) != (vigenteHasta != null)) {
            throw new IllegalArgumentException("solo la suspension tiene fecha fin, y la tiene siempre");
        }
    }

    /** Si restringe la cuenta en este momento: suspension no vencida o baneo, no revertidos. */
    public boolean restringeEn(OffsetDateTime ahora) {
        if (revertidaEn != null || tipo == Tipo.ADVERTENCIA) {
            return false;
        }
        return tipo == Tipo.BANEO || vigenteHasta.isAfter(ahora);
    }

    /** Si todavia se puede apelar o revertir: no revertida y, si es suspension, no vencida. */
    public boolean estaVigenteEn(OffsetDateTime ahora) {
        return revertidaEn == null && (tipo != Tipo.SUSPENSION || vigenteHasta.isAfter(ahora));
    }

    /** Levanta la sancion (apelacion favorable, HU-USR-007 CA-04). Idempotente. */
    public void revertir(UUID quien, String motivo, OffsetDateTime ahora) {
        if (revertidaEn != null) {
            return;
        }
        this.revertidaEn = Objects.requireNonNull(ahora);
        this.revertidaPor = Objects.requireNonNull(quien);
        this.motivoReversion = motivo;
    }

    /** Acorta una suspension (apelacion con decision REDUCIR). */
    public void reducirHasta(OffsetDateTime nuevaVigencia, OffsetDateTime ahora) {
        if (tipo != Tipo.SUSPENSION) {
            throw new IllegalArgumentException("solo una suspension se puede reducir");
        }
        Objects.requireNonNull(nuevaVigencia);
        if (!nuevaVigencia.isBefore(vigenteHasta)) {
            throw new IllegalArgumentException("reducir es acortar: la nueva fecha fin tiene que ser anterior");
        }
        if (nuevaVigencia.isBefore(ahora)) {
            throw new IllegalArgumentException("la nueva fecha fin no puede estar en el pasado");
        }
        this.vigenteHasta = nuevaVigencia;
    }

    public UUID id() {
        return id;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public Tipo tipo() {
        return tipo;
    }

    public String motivo() {
        return motivo;
    }

    public String politica() {
        return politica;
    }

    public String comentarioId() {
        return comentarioId;
    }

    public UUID emitidaPor() {
        return emitidaPor;
    }

    public String rolEmisor() {
        return rolEmisor;
    }

    public OffsetDateTime emitidaEn() {
        return emitidaEn;
    }

    public OffsetDateTime vigenteHasta() {
        return vigenteHasta;
    }

    public OffsetDateTime revertidaEn() {
        return revertidaEn;
    }

    public UUID revertidaPor() {
        return revertidaPor;
    }

    public String motivoReversion() {
        return motivoReversion;
    }
}
