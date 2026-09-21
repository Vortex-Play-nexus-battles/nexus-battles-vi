package com.nexusbattles.plataforma.torneos.torneo;

import java.time.OffsetDateTime;

/** Rechazo de negocio con el motivo del contrato (torneos.yaml, ProblemDetail.motivo). */
public class TorneoRechazado extends RuntimeException {

    public enum Motivo {
        PERMISO_INSUFICIENTE,
        SOLICITUD_INVALIDA,
        NO_ENCONTRADO,
        VENTANA_DE_91_DIAS,
        ESTADO_NO_PERMITE,
        JUGADOR_YA_EN_EQUIPO,
        NOMBRE_RECHAZADO,
        LISTA_NEGRA_NO_DISPONIBLE,
        CUPO_AGOTADO,
        YA_INSCRITO,
        CREDITOS_INSUFICIENTES,
        INTEGRANTE_SANCIONADO,
        LIBRO_NO_DISPONIBLE,
        SANCIONES_NO_DISPONIBLES,
        SIN_EQUIPOS,
        ENCUENTRO_NO_LISTO,
        GANADOR_NO_PARTICIPA
    }

    private final Motivo motivo;
    private final OffsetDateTime proximaFechaPosible;

    public TorneoRechazado(Motivo motivo, String detalle) {
        this(motivo, detalle, null);
    }

    public TorneoRechazado(Motivo motivo, String detalle, OffsetDateTime proximaFechaPosible) {
        super(detalle);
        this.motivo = motivo;
        this.proximaFechaPosible = proximaFechaPosible;
    }

    public Motivo motivo() {
        return motivo;
    }

    public OffsetDateTime proximaFechaPosible() {
        return proximaFechaPosible;
    }
}
