package com.nexusbattles.ms_identidad.privacidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Una solicitud de cierre de la propia cuenta — derecho al olvido (HU-PRV-005,
 * RF-PRV-005, 7.3.6).
 *
 * <p>Nace {@link #PROGRAMADA} para dentro de {@link #PLAZO_DIAS} dias; hasta
 * entonces la persona la puede {@link #cancelar cancelar}. Al vencer, la tarea
 * programada anonimiza la cuenta ({@link AnonimizadorDeCuentas}) y la marca
 * {@link #EJECUTADA}. Las transiciones solo salen de {@code PROGRAMADA}: una
 * solicitud cancelada o ejecutada no vuelve atras, y repetir una transicion no
 * cambia nada (idempotente).
 *
 * <p>No guarda ningun dato personal: el {@code uid} de la cuenta y fechas.
 */
@Entity
@Table(name = "solicitudes_cierre_cuenta")
public class SolicitudDeCierre {

    /**
     * RN-USR-011: «La solicitud de eliminacion completa de la cuenta y sus
     * datos debe completarse en un plazo de treinta (30) dias». Es del
     * requisito, no un ajuste del despliegue: no se lee de configuracion, y
     * la base (V6) tampoco acepta otro.
     */
    public static final int PLAZO_DIAS = 30;
    public static final Duration PLAZO = Duration.ofDays(PLAZO_DIAS);

    public static final String PROGRAMADA = "PROGRAMADA";
    public static final String CANCELADA = "CANCELADA";
    public static final String EJECUTADA = "EJECUTADA";

    @Id
    private UUID id;

    @Column(name = "usuario_uid", nullable = false)
    private UUID usuarioUid;

    @Column(nullable = false, length = 16)
    private String estado;

    @Column(name = "solicitada_en", nullable = false)
    private LocalDateTime solicitadaEn;

    @Column(name = "programada_para", nullable = false)
    private LocalDateTime programadaPara;

    @Column(name = "cancelada_en")
    private LocalDateTime canceladaEn;

    @Column(name = "ejecutada_en")
    private LocalDateTime ejecutadaEn;

    protected SolicitudDeCierre() {
    }

    /**
     * Programa el cierre de la cuenta {@code usuarioUid} para dentro del
     * plazo. Al segundo: la base compara las dos fechas exactas.
     */
    public static SolicitudDeCierre programar(UUID usuarioUid, LocalDateTime ahora) {
        if (usuarioUid == null || ahora == null) {
            throw new IllegalArgumentException("La solicitud de cierre necesita la cuenta y la hora");
        }
        SolicitudDeCierre solicitud = new SolicitudDeCierre();
        solicitud.id = UUID.randomUUID();
        solicitud.usuarioUid = usuarioUid;
        solicitud.estado = PROGRAMADA;
        solicitud.solicitadaEn = ahora.truncatedTo(ChronoUnit.SECONDS);
        solicitud.programadaPara = solicitud.solicitadaEn.plusDays(PLAZO_DIAS);
        return solicitud;
    }

    public boolean estaProgramada() {
        return PROGRAMADA.equals(estado);
    }

    /** Programada y con el plazo cumplido: la tarea ya la puede ejecutar. */
    public boolean vencida(LocalDateTime ahora) {
        return estaProgramada() && !ahora.isBefore(programadaPara);
    }

    /** La persona se arrepintio antes del plazo. Sin efecto si ya no estaba programada. */
    public void cancelar(LocalDateTime ahora) {
        if (estaProgramada()) {
            estado = CANCELADA;
            canceladaEn = ahora;
        }
    }

    /** La cuenta quedo anonimizada. Sin efecto si ya no estaba programada. */
    public void marcarEjecutada(LocalDateTime ahora) {
        if (estaProgramada()) {
            estado = EJECUTADA;
            ejecutadaEn = ahora;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioUid() {
        return usuarioUid;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDateTime getSolicitadaEn() {
        return solicitadaEn;
    }

    public LocalDateTime getProgramadaPara() {
        return programadaPara;
    }

    public LocalDateTime getCanceladaEn() {
        return canceladaEn;
    }

    public LocalDateTime getEjecutadaEn() {
        return ejecutadaEn;
    }
}
