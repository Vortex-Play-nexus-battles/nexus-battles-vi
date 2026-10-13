package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Quien bloqueo a quien en los mensajes privados — puerto de salida (D-40).
 *
 * <p>Tabla propia, {@code bloqueos_mensajes_directos} (V15): un bloqueo es una
 * relacion entre dos jugadores, no una propiedad de un mensaje.
 */
public interface RepositorioDeBloqueos {

    /**
     * Anota que {@code quien} bloqueo a {@code aQuien}. Idempotente.
     *
     * @return {@code true} si no estaba bloqueado
     */
    boolean bloquear(UUID quien, UUID aQuien, Instant cuando);

    /**
     * Quita el bloqueo. Idempotente.
     *
     * @return {@code true} si estaba bloqueado
     */
    boolean desbloquear(UUID quien, UUID aQuien);

    /** Si {@code quien} tiene bloqueado a {@code aQuien}. */
    boolean bloqueo(UUID quien, UUID aQuien);

    /** A quienes tiene bloqueados ese jugador. */
    Set<UUID> bloqueadosPor(UUID quien);

    /** Quienes tienen bloqueado a ese jugador. */
    Set<UUID> quienesBloquearonA(UUID jugador);
}
