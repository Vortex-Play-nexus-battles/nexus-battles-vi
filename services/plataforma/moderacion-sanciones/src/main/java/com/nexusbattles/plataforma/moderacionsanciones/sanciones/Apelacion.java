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
 * Una apelacion sobre una sancion — HU-USR-007.
 *
 * <p>La abre el sancionado (una abierta por sancion como maximo, dentro del
 * plazo) y la cierra el panel de revision con una decision motivada:
 * mantener, reducir o revertir. La decision queda aqui; su efecto sobre la
 * sancion lo aplica {@link SancionesService}.
 */
@Entity
@Table(name = "apelaciones")
public class Apelacion {

    /** Estado de la apelacion: abierta, o cerrada con la decision tomada. */
    public enum Estado { PENDIENTE, MANTENIDA, REDUCIDA, REVERTIDA }

    @Id
    private UUID id;

    @Column(name = "sancion_id", nullable = false)
    private UUID sancionId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 2000)
    private String argumento;

    @Column(name = "creada_en", nullable = false)
    private OffsetDateTime creadaEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "decision_motivo", length = 1000)
    private String decisionMotivo;

    @Column(name = "resuelta_por")
    private UUID resueltaPor;

    @Column(name = "resuelta_en")
    private OffsetDateTime resueltaEn;

    @Column(name = "nueva_vigencia")
    private OffsetDateTime nuevaVigencia;

    protected Apelacion() {
    }

    public Apelacion(UUID id, UUID sancionId, UUID usuarioId, String argumento, OffsetDateTime creadaEn) {
        this.id = Objects.requireNonNull(id);
        this.sancionId = Objects.requireNonNull(sancionId);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.argumento = Objects.requireNonNull(argumento);
        this.creadaEn = Objects.requireNonNull(creadaEn);
        this.estado = Estado.PENDIENTE;
    }

    /** Cierra la apelacion con la decision del panel. Solo una vez. */
    public void resolver(Estado decision, String motivo, UUID quien, OffsetDateTime ahora, OffsetDateTime nuevaVigencia) {
        if (estado != Estado.PENDIENTE) {
            throw new IllegalStateException("la apelacion ya esta resuelta");
        }
        if (decision == Estado.PENDIENTE) {
            throw new IllegalArgumentException("resolver es decidir: MANTENIDA, REDUCIDA o REVERTIDA");
        }
        this.estado = decision;
        this.decisionMotivo = Objects.requireNonNull(motivo);
        this.resueltaPor = Objects.requireNonNull(quien);
        this.resueltaEn = Objects.requireNonNull(ahora);
        this.nuevaVigencia = nuevaVigencia;
    }

    public UUID id() {
        return id;
    }

    public UUID sancionId() {
        return sancionId;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public String argumento() {
        return argumento;
    }

    public OffsetDateTime creadaEn() {
        return creadaEn;
    }

    public Estado estado() {
        return estado;
    }

    public String decisionMotivo() {
        return decisionMotivo;
    }

    public UUID resueltaPor() {
        return resueltaPor;
    }

    public OffsetDateTime resueltaEn() {
        return resueltaEn;
    }

    public OffsetDateTime nuevaVigencia() {
        return nuevaVigencia;
    }
}
