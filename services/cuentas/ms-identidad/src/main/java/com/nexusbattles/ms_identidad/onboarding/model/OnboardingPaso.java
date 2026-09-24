package com.nexusbattles.ms_identidad.onboarding.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Un paso del alta de un jugador (tabla {@code onboarding_paso}, V2).
 *
 * <p>{@code detalle} guarda lo que el servicio dueno devolvio (la transaccion de
 * creditos, los elementos creados en inventario): es la evidencia de que el
 * paso se hizo y lo que permite no repetirlo. {@code ultimoError} es interno;
 * al jugador se le muestra un motivo legible, nunca el error tecnico.
 */
@Entity
@Table(name = "onboarding_paso")
@IdClass(OnboardingPaso.Clave.class)
public class OnboardingPaso {

    @Id
    @Column(name = "usuario_uid", nullable = false)
    private UUID usuarioUid;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PasoOnboarding paso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoPaso estado;

    @Column(length = 500)
    private String detalle;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    protected OnboardingPaso() {
    }

    public OnboardingPaso(UUID usuarioUid, PasoOnboarding paso, EstadoPaso estado, String detalle,
                          LocalDateTime ahora) {
        this.usuarioUid = usuarioUid;
        this.paso = paso;
        this.estado = estado;
        this.detalle = detalle;
        this.intentos = 0;
        this.actualizadoEn = ahora;
    }

    public UUID getUsuarioUid() { return usuarioUid; }
    public PasoOnboarding getPaso() { return paso; }
    public EstadoPaso getEstado() { return estado; }
    public String getDetalle() { return detalle; }
    public int getIntentos() { return intentos; }
    public String getUltimoError() { return ultimoError; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }

    public boolean hecho() {
        return estado == EstadoPaso.HECHO;
    }

    public void marcarHecho(String detalle, LocalDateTime ahora) {
        this.estado = EstadoPaso.HECHO;
        this.detalle = OnboardingJugador.recortar(detalle);
        this.ultimoError = null;
        this.intentos++;
        this.actualizadoEn = ahora;
    }

    public void marcarError(String error, LocalDateTime ahora) {
        this.estado = EstadoPaso.ERROR;
        this.ultimoError = OnboardingJugador.recortar(error);
        this.intentos++;
        this.actualizadoEn = ahora;
    }

    /** Clave compuesta (uid, paso). */
    public static class Clave implements Serializable {
        private UUID usuarioUid;
        private PasoOnboarding paso;

        public Clave() {
        }

        public Clave(UUID usuarioUid, PasoOnboarding paso) {
            this.usuarioUid = usuarioUid;
            this.paso = paso;
        }

        @Override
        public boolean equals(Object otro) {
            if (this == otro) {
                return true;
            }
            if (!(otro instanceof Clave clave)) {
                return false;
            }
            return Objects.equals(usuarioUid, clave.usuarioUid) && paso == clave.paso;
        }

        @Override
        public int hashCode() {
            return Objects.hash(usuarioUid, paso);
        }
    }
}
