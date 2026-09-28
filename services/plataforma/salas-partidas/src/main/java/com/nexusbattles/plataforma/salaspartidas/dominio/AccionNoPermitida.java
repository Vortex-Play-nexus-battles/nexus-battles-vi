package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;
import java.util.Objects;

/**
 * El motor de combate rechazo la accion — 409 {@code accion-no-permitida}.
 *
 * <p>La valida el servidor y no el cliente (canal 1.5.0): una accion en carga,
 * que no se aprendio en su nivel, contra un companero, de un sanador que
 * ataca... No se aplico nada y el turno sigue siendo del jugador.
 * {@link #motivo()} es el {@code MotivoDeRechazo} del motor, tal cual.
 */
public class AccionNoPermitida extends ErrorDeNegocio {

    public static final URI TIPO = URI.create("https://nexusbattles.local/errores/accion-no-permitida");

    private final String motivo;

    public AccionNoPermitida(String motivo, String detalle) {
        super(TIPO, "La accion no se puede jugar ahora", 409,
                detalle == null || detalle.isBlank() ? "El motor de combate rechazo la accion." : detalle);
        this.motivo = Objects.requireNonNullElse(motivo, "ACCION_DESCONOCIDA");
    }

    public String motivo() {
        return motivo;
    }
}
