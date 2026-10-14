package com.nexusbattles.plataforma.adminparametros.parametros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Una version del historial de un parametro (CA-01: quien, cuando, valor anterior y nuevo). */
@Entity
@Table(name = "versiones")
public class Version {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String clave;

    @Column(nullable = false)
    private int version;

    @Column(name = "valor_anterior", length = 200)
    private String valorAnterior;

    @Column(name = "valor_nuevo", length = 200)
    private String valorNuevo;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Column(name = "cambiado_por", nullable = false)
    private UUID cambiadoPor;

    @Column(name = "cambiado_en", nullable = false)
    private OffsetDateTime cambiadoEn;

    @Column(name = "vigente_desde", nullable = false)
    private OffsetDateTime vigenteDesde;

    /** SA-5 (V7): la IP de quien hizo el cambio, la que fija el borde. */
    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    /** SA-5 (V7): cuando entro en la bitacora central; nulo mientras esta pendiente. */
    @Column(name = "auditado_en")
    private OffsetDateTime auditadoEn;

    @Column(name = "intentos_de_auditoria", nullable = false)
    private int intentosDeAuditoria;

    @Column(name = "error_de_auditoria", length = 300)
    private String errorDeAuditoria;

    @Column(name = "proximo_intento_de_auditoria")
    private OffsetDateTime proximoIntentoDeAuditoria;

    protected Version() {
    }

    public Version(Long id, String clave, int version, String valorAnterior, String valorNuevo, String motivo,
                   UUID cambiadoPor, OffsetDateTime cambiadoEn, OffsetDateTime vigenteDesde) {
        this.id = id;
        this.clave = clave;
        this.version = version;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
        this.motivo = motivo;
        this.cambiadoPor = cambiadoPor;
        this.cambiadoEn = cambiadoEn;
        this.vigenteDesde = vigenteDesde;
    }

    public Long id() { return id; }
    public String clave() { return clave; }
    public int version() { return version; }
    public String valorAnterior() { return valorAnterior; }
    public String valorNuevo() { return valorNuevo; }
    public String motivo() { return motivo; }
    public UUID cambiadoPor() { return cambiadoPor; }
    public OffsetDateTime cambiadoEn() { return cambiadoEn; }
    public OffsetDateTime vigenteDesde() { return vigenteDesde; }
    public String ipOrigen() { return ipOrigen; }
    public OffsetDateTime auditadoEn() { return auditadoEn; }
    public int intentosDeAuditoria() { return intentosDeAuditoria; }
    public String errorDeAuditoria() { return errorDeAuditoria; }
    public OffsetDateTime proximoIntentoDeAuditoria() { return proximoIntentoDeAuditoria; }

    /** SA-5: desde donde se hizo el cambio (antes de guardar la version). */
    public void registrarOrigen(String ip) {
        this.ipOrigen = ip;
    }

    /** SA-5: entro en la bitacora central. */
    public void auditado(OffsetDateTime ahora) {
        this.auditadoEn = java.util.Objects.requireNonNull(ahora);
        this.intentosDeAuditoria++;
        this.errorDeAuditoria = null;
        this.proximoIntentoDeAuditoria = null;
    }

    /** SA-5: un intento que no entro; la espera crece con cada fallo hasta {@code maxima}. */
    public void falloDeAuditoria(String motivo, OffsetDateTime ahora, java.time.Duration base,
                                 java.time.Duration maxima) {
        this.intentosDeAuditoria++;
        String texto = motivo == null ? "" : motivo;
        this.errorDeAuditoria = texto.length() <= 300 ? texto : texto.substring(0, 300);
        java.time.Duration espera = base;
        for (int i = 1; i < intentosDeAuditoria && espera.compareTo(maxima) < 0; i++) {
            espera = espera.multipliedBy(2);
        }
        this.proximoIntentoDeAuditoria = ahora.plus(espera.compareTo(maxima) > 0 ? maxima : espera);
    }
}
