package com.nexusbattles.plataforma.adminparametros.politicas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Un cambio publicado en una politica de uso (SA-11, §7.3.7). Inmutable una
 * vez publicado: lo que cambia despues es solo su paso por la bitacora
 * central, con la misma espera creciente que las versiones de parametros.
 */
@Entity
@Table(name = "cambios_de_politica")
public class CambioDePolitica {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Politica politica;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 2000)
    private String descripcion;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Column(name = "vigente_desde", nullable = false)
    private OffsetDateTime vigenteDesde;

    @Column(name = "publicado_por", nullable = false)
    private UUID publicadoPor;

    @Column(name = "publicado_en", nullable = false)
    private OffsetDateTime publicadoEn;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    @Column(name = "auditado_en")
    private OffsetDateTime auditadoEn;

    @Column(name = "intentos_de_auditoria", nullable = false)
    private int intentosDeAuditoria;

    @Column(name = "error_de_auditoria", length = 300)
    private String errorDeAuditoria;

    @Column(name = "proximo_intento_de_auditoria")
    private OffsetDateTime proximoIntentoDeAuditoria;

    protected CambioDePolitica() {
    }

    public CambioDePolitica(UUID id, Politica politica, String titulo, String descripcion, String motivo,
                            OffsetDateTime vigenteDesde, UUID publicadoPor, OffsetDateTime publicadoEn,
                            String ipOrigen) {
        this.id = Objects.requireNonNull(id);
        this.politica = Objects.requireNonNull(politica);
        this.titulo = Objects.requireNonNull(titulo);
        this.descripcion = Objects.requireNonNull(descripcion);
        this.motivo = Objects.requireNonNull(motivo);
        this.vigenteDesde = Objects.requireNonNull(vigenteDesde);
        this.publicadoPor = Objects.requireNonNull(publicadoPor);
        this.publicadoEn = Objects.requireNonNull(publicadoEn);
        this.ipOrigen = ipOrigen;
    }

    public UUID id() {
        return id;
    }

    public Politica politica() {
        return politica;
    }

    public String titulo() {
        return titulo;
    }

    public String descripcion() {
        return descripcion;
    }

    public String motivo() {
        return motivo;
    }

    public OffsetDateTime vigenteDesde() {
        return vigenteDesde;
    }

    public UUID publicadoPor() {
        return publicadoPor;
    }

    public OffsetDateTime publicadoEn() {
        return publicadoEn;
    }

    public String ipOrigen() {
        return ipOrigen;
    }

    public OffsetDateTime auditadoEn() {
        return auditadoEn;
    }

    public int intentosDeAuditoria() {
        return intentosDeAuditoria;
    }

    public String errorDeAuditoria() {
        return errorDeAuditoria;
    }

    /** Entro en la bitacora central. */
    public void auditado(OffsetDateTime ahora) {
        this.auditadoEn = Objects.requireNonNull(ahora);
        this.intentosDeAuditoria++;
        this.errorDeAuditoria = null;
        this.proximoIntentoDeAuditoria = null;
    }

    /** Un intento que no entro; la espera crece con cada fallo hasta {@code maxima}. */
    public void falloDeAuditoria(String motivoDelFallo, OffsetDateTime ahora, Duration base, Duration maxima) {
        this.intentosDeAuditoria++;
        String texto = motivoDelFallo == null ? "" : motivoDelFallo;
        this.errorDeAuditoria = texto.length() <= 300 ? texto : texto.substring(0, 300);
        Duration espera = base;
        for (int i = 1; i < intentosDeAuditoria && espera.compareTo(maxima) < 0; i++) {
            espera = espera.multipliedBy(2);
        }
        this.proximoIntentoDeAuditoria = ahora.plus(espera.compareTo(maxima) > 0 ? maxima : espera);
    }
}
