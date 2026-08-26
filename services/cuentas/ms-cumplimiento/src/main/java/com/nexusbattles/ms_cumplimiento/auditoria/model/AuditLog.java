package com.nexusbattles.ms_cumplimiento.auditoria.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private Instant fechaHora;

    @Column(name = "administrador_id", nullable = false, updatable = false)
    private String administradorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_accion", nullable = false, updatable = false, length = 40)
    private AuditActionType tipoAccion;

    @Column(name = "afectado", nullable = false, updatable = false)
    private String afectado;

    @Lob
    @Column(name = "valor_anterior", updatable = false)
    private String valorAnterior;

    @Lob
    @Column(name = "valor_nuevo", updatable = false)
    private String valorNuevo;

    @Column(name = "motivo", nullable = false, updatable = false, length = 500)
    private String motivo;

    @Column(name = "ip_origen", nullable = false, updatable = false, length = 45)
    private String ipOrigen;

    protected AuditLog() {
        // Requerido por JPA
    }

    private AuditLog(Builder b) {
        this.fechaHora = Instant.now();
        this.administradorId = b.administradorId;
        this.tipoAccion = b.tipoAccion;
        this.afectado = b.afectado;
        this.valorAnterior = b.valorAnterior;
        this.valorNuevo = b.valorNuevo;
        this.motivo = b.motivo;
        this.ipOrigen = b.ipOrigen;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() { return id; }
    public Instant getFechaHora() { return fechaHora; }
    public String getAdministradorId() { return administradorId; }
    public AuditActionType getTipoAccion() { return tipoAccion; }
    public String getAfectado() { return afectado; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNuevo() { return valorNuevo; }
    public String getMotivo() { return motivo; }
    public String getIpOrigen() { return ipOrigen; }

    public static class Builder {
        private String administradorId;
        private AuditActionType tipoAccion;
        private String afectado;
        private String valorAnterior;
        private String valorNuevo;
        private String motivo;
        private String ipOrigen;

        public Builder administradorId(String v) { this.administradorId = v; return this; }
        public Builder tipoAccion(AuditActionType v) { this.tipoAccion = v; return this; }
        public Builder afectado(String v) { this.afectado = v; return this; }
        public Builder valorAnterior(String v) { this.valorAnterior = v; return this; }
        public Builder valorNuevo(String v) { this.valorNuevo = v; return this; }
        public Builder motivo(String v) { this.motivo = v; return this; }
        public Builder ipOrigen(String v) { this.ipOrigen = v; return this; }

        public AuditLog build() {
            if (administradorId == null || tipoAccion == null || afectado == null
                    || motivo == null || ipOrigen == null) {
                throw new IllegalStateException(
                        "Faltan campos obligatorios para construir un AuditLog (HU-AUD-001)");
            }
            return new AuditLog(this);
        }
    }
}