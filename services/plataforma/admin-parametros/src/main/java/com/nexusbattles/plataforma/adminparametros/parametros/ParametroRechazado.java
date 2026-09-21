package com.nexusbattles.plataforma.adminparametros.parametros;

/** Rechazo con el motivo del contrato (admin-parametros.yaml, ProblemDetail.motivo). */
public class ParametroRechazado extends RuntimeException {

    public enum Motivo { PERMISO_INSUFICIENTE, SOLICITUD_INVALIDA, VALOR_INVALIDO, NO_ENCONTRADO, INALTERABLE }

    private final Motivo motivo;

    public ParametroRechazado(Motivo motivo, String detalle) {
        super(detalle);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
