package com.nexusbattles.ms_identidad.onboarding.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cabecera del alta de un jugador (tabla {@code onboarding_jugador}, V2).
 *
 * <p>Una fila por jugador creado desde R17. Las cuentas anteriores no tienen
 * fila: el alta automatica no existia cuando se crearon y no se les fabrica un
 * estado inicial a posteriori (quedan como «no aplica»).
 */
@Entity
@Table(name = "onboarding_jugador")
public class OnboardingJugador {

    @Id
    @Column(name = "usuario_uid", nullable = false)
    private UUID usuarioUid;

    @Column(name = "version_bootstrap", nullable = false)
    private int versionBootstrap;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EstadoOnboarding estado;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Column(name = "siguiente_intento")
    private LocalDateTime siguienteIntento;

    /** Hasta cuando es valido el turno de quien lo esta procesando. */
    @Column(name = "en_proceso_hasta")
    private LocalDateTime enProcesoHasta;

    /** trace-id W3C (32 hex) con el que se siguen todos los intentos del alta. */
    @Column(length = 32)
    private String traza;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "completado_en")
    private LocalDateTime completadoEn;

    protected OnboardingJugador() {
    }

    public OnboardingJugador(UUID usuarioUid, int versionBootstrap, String traza, LocalDateTime ahora) {
        this.usuarioUid = usuarioUid;
        this.versionBootstrap = versionBootstrap;
        this.estado = EstadoOnboarding.PENDIENTE;
        this.intentos = 0;
        this.traza = traza;
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
        this.siguienteIntento = ahora;
    }

    public UUID getUsuarioUid() { return usuarioUid; }
    public int getVersionBootstrap() { return versionBootstrap; }
    public EstadoOnboarding getEstado() { return estado; }
    public int getIntentos() { return intentos; }
    public String getUltimoError() { return ultimoError; }
    public LocalDateTime getSiguienteIntento() { return siguienteIntento; }
    public LocalDateTime getEnProcesoHasta() { return enProcesoHasta; }
    public String getTraza() { return traza; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public LocalDateTime getCompletadoEn() { return completadoEn; }

    /** Todos los pasos respondieron que si. */
    public void completar(LocalDateTime ahora) {
        this.estado = EstadoOnboarding.COMPLETO;
        this.ultimoError = null;
        this.siguienteIntento = null;
        this.enProcesoHasta = null;
        this.completadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    /** Algun paso fallo: se deja para mas tarde, con el motivo resumido. */
    public void aplazar(String motivo, LocalDateTime siguiente, LocalDateTime ahora) {
        this.estado = EstadoOnboarding.ERROR_REINTENTABLE;
        this.ultimoError = recortar(motivo);
        this.siguienteIntento = siguiente;
        this.enProcesoHasta = null;
        this.actualizadoEn = ahora;
    }

    static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= 500 ? texto : texto.substring(0, 497) + "...";
    }
}
