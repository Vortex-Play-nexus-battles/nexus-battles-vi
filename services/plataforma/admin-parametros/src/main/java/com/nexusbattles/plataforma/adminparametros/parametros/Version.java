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
}
