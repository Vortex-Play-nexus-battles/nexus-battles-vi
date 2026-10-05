package com.nexusbattles.plataforma.torneos.torneo;

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
 * Un torneo — RF-TOR-001. Ocho cupos, un torneo cada 91 dias (valor
 * inalterable del Charter), estado que solo avanza:
 * INSCRIPCIONES_ABIERTAS → EN_CURSO → FINALIZADO, o CANCELADO antes de empezar.
 */
@Entity
@Table(name = "torneos")
public class Torneo {

    /** Cupos del arbol (RF-TOR-004: ocho equipos). */
    public static final int CUPOS = 8;

    /** RF-TOR-001: «un (1) torneo cada noventa y un (91) dias». */
    public static final int DIAS_ENTRE_TORNEOS = 91;

    public enum Estado {
        INSCRIPCIONES_ABIERTAS,
        EN_CURSO,
        FINALIZADO,
        CANCELADO
    }

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Estado estado;

    @Column(name = "creado_por", nullable = false)
    private UUID creadoPor;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "inscripciones_cierran_en", nullable = false)
    private OffsetDateTime inscripcionesCierranEn;

    @Column(name = "costo_inscripcion", nullable = false)
    private int costoInscripcion;

    @Column(name = "iniciado_en")
    private OffsetDateTime iniciadoEn;

    @Column(name = "finalizado_en")
    private OffsetDateTime finalizadoEn;

    @Column(name = "campeon_equipo_id")
    private UUID campeonEquipoId;

    @Column(name = "motivo_cancelacion", length = 500)
    private String motivoCancelacion;

    /**
     * Premio anunciado al crear el torneo (RF-TOR-007, torneos.yaml 1.2.0).
     * Nulo en los torneos creados antes de B10: para esos manda la
     * configuracion vigente al jugarse la final.
     */
    @Column(name = "premio_creditos_por_integrante")
    private Integer premioCreditosPorIntegrante;

    @Column(name = "premio_epica_producto_id", length = 100)
    private String premioEpicaProductoId;

    protected Torneo() {
    }

    public Torneo(UUID id, String nombre, UUID creadoPor, OffsetDateTime creadoEn,
                  OffsetDateTime inscripcionesCierranEn, int costoInscripcion) {
        this(id, nombre, creadoPor, creadoEn, inscripcionesCierranEn, costoInscripcion, null);
    }

    public Torneo(UUID id, String nombre, UUID creadoPor, OffsetDateTime creadoEn,
                  OffsetDateTime inscripcionesCierranEn, int costoInscripcion, PoliticaDePremio.Premio premio) {
        this.id = Objects.requireNonNull(id);
        this.nombre = Objects.requireNonNull(nombre);
        this.creadoPor = Objects.requireNonNull(creadoPor);
        this.creadoEn = Objects.requireNonNull(creadoEn);
        this.inscripcionesCierranEn = Objects.requireNonNull(inscripcionesCierranEn);
        if (costoInscripcion < 0) {
            throw new IllegalArgumentException("el costo de inscripcion no puede ser negativo");
        }
        this.costoInscripcion = costoInscripcion;
        this.estado = Estado.INSCRIPCIONES_ABIERTAS;
        if (premio != null) {
            this.premioCreditosPorIntegrante = premio.creditosPorIntegrante();
            this.premioEpicaProductoId = premio.epicaProductoId();
        }
    }

    /**
     * El premio de este torneo: el que se anuncio al crearlo o, si es anterior
     * a B10 y no tiene uno, el de la configuracion vigente.
     */
    public PoliticaDePremio.Premio premio(PoliticaDePremio vigente) {
        if (premioCreditosPorIntegrante == null) {
            return vigente.premio();
        }
        return new PoliticaDePremio.Premio(premioCreditosPorIntegrante, premioEpicaProductoId);
    }

    public boolean admiteInscripciones() {
        return estado == Estado.INSCRIPCIONES_ABIERTAS;
    }

    public boolean enCurso() {
        return estado == Estado.EN_CURSO;
    }

    public void iniciar(OffsetDateTime ahora) {
        if (!admiteInscripciones()) {
            throw new IllegalStateException("el torneo no esta en inscripciones abiertas");
        }
        this.estado = Estado.EN_CURSO;
        this.iniciadoEn = ahora;
    }

    public void finalizar(UUID campeon, OffsetDateTime ahora) {
        if (!enCurso()) {
            throw new IllegalStateException("el torneo no esta en curso");
        }
        this.estado = Estado.FINALIZADO;
        this.campeonEquipoId = Objects.requireNonNull(campeon);
        this.finalizadoEn = ahora;
    }

    public void cancelar(String motivo, OffsetDateTime ahora) {
        if (!admiteInscripciones()) {
            throw new IllegalStateException("solo se cancela antes del inicio");
        }
        this.estado = Estado.CANCELADO;
        this.motivoCancelacion = motivo;
        this.finalizadoEn = ahora;
    }

    public UUID id() { return id; }
    public String nombre() { return nombre; }
    public Estado estado() { return estado; }
    public UUID creadoPor() { return creadoPor; }
    public OffsetDateTime creadoEn() { return creadoEn; }
    public OffsetDateTime inscripcionesCierranEn() { return inscripcionesCierranEn; }
    public int costoInscripcion() { return costoInscripcion; }
    public OffsetDateTime iniciadoEn() { return iniciadoEn; }
    public OffsetDateTime finalizadoEn() { return finalizadoEn; }
    public UUID campeonEquipoId() { return campeonEquipoId; }
    public String motivoCancelacion() { return motivoCancelacion; }
}
