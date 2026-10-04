package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * El mensaje privado no se entrego, con su motivo — B6.
 *
 * <p>Es un {@link ErrorDeNegocio} para que la via REST lo convierta en
 * problem details como cualquier otro (regla 4). La via STOMP lo convierte en
 * {@code {tipo: RECHAZO, motivo, idCliente}} por la cola privada de quien
 * escribio; de ahi que lleve el {@code idCliente}: sin el, el navegador no
 * sabria cual de sus mensajes pendientes fue el rechazado.
 */
public class MensajeDirectoRechazado extends ErrorDeNegocio {

    private final MotivoDeRechazo motivo;
    private final String idCliente;
    private final Duration reintentarEn;

    public MensajeDirectoRechazado(MotivoDeRechazo motivo, String idCliente) {
        this(motivo, idCliente, null);
    }

    /**
     * @param reintentarEn solo con {@link MotivoDeRechazo#DEMASIADO_RAPIDO}:
     *                     cuanto falta para que el limite deje pasar otro
     */
    public MensajeDirectoRechazado(MotivoDeRechazo motivo, String idCliente, Duration reintentarEn) {
        super(Objects.requireNonNull(motivo, "Un rechazo sin motivo no se puede explicar.").tipo(),
                motivo.titulo(), motivo.estado(), motivo.detalle());
        this.motivo = motivo;
        this.idCliente = idCliente;
        this.reintentarEn = reintentarEn;
    }

    public MotivoDeRechazo motivo() {
        return motivo;
    }

    /** El del envio rechazado, o {@code null} si el cliente no puso ninguno. */
    public String idCliente() {
        return idCliente;
    }

    /** Segundos enteros hacia arriba, para {@code Retry-After}. */
    public Optional<Long> reintentarEnSegundos() {
        if (reintentarEn == null) {
            return Optional.empty();
        }
        long milis = Math.max(reintentarEn.toMillis(), 0);
        return Optional.of(Math.max(1, (milis + 999) / 1000));
    }
}
