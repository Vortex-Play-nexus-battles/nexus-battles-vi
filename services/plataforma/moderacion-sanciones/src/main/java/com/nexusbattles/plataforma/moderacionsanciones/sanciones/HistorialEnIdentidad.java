package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.web.client.HttpClientErrorException;

import java.util.Objects;
import java.util.UUID;

/**
 * Destino del canal HISTORIAL — SA-3 (§7.3.4, filtro del directorio por
 * historial de sanciones; ms-identidad-admin.yaml 1.7.0, historial-sanciones).
 *
 * <p>Como la proyeccion, <b>se manda el resumen de AHORA, no el del evento</b>:
 * al entregar se cuentan las sanciones de la cuenta y se busca la mas reciente.
 * Asi, si identidad estuvo caida y llegan tarde o desordenadas varias salidas
 * de la misma cuenta, cada entrega manda el valor bueno y la ultima gana. Las
 * revertidas cuentan: es historial, no restriccion.
 */
public class HistorialEnIdentidad implements DestinoDeSalidas {

    private final ClienteIdentidad identidad;
    private final SancionRepository sanciones;

    public HistorialEnIdentidad(ClienteIdentidad identidad, SancionRepository sanciones) {
        this.identidad = Objects.requireNonNull(identidad);
        this.sanciones = Objects.requireNonNull(sanciones);
    }

    @Override
    public CanalDeSalida canal() {
        return CanalDeSalida.HISTORIAL;
    }

    @Override
    public Resultado entregar(SalidaPendiente salida) {
        try {
            identidad.historial(salida.usuarioId(), resumenDe(salida.usuarioId()));
            return Resultado.ENTREGADO;
        } catch (HttpClientErrorException error) {
            return Respuestas.rechazoOReintento(error);
        }
    }

    /** El resumen del historial de la cuenta, hoy. */
    ClienteIdentidad.HistorialDeSanciones resumenDe(UUID usuarioId) {
        long total = sanciones.countByUsuarioId(usuarioId);
        return new ClienteIdentidad.HistorialDeSanciones(Math.toIntExact(total),
                total == 0 ? null
                        : sanciones.findFirstByUsuarioIdOrderByEmitidaEnDesc(usuarioId)
                                .map(Sancion::emitidaEn).orElse(null));
    }
}
