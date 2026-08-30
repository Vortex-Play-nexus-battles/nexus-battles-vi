package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import java.util.Objects;
import java.util.UUID;

/**
 * Comprobante de una reserva de creditos.
 *
 * <p>Lo emite el modulo de creditos; aqui solo se guarda para poder liberarla si
 * la sala no llega a existir.
 *
 * @param id       identificador de la reserva, emitido por el proveedor
 * @param creditos creditos efectivamente reservados
 */
public record ReservaDeCreditos(UUID id, int creditos) {

    public ReservaDeCreditos {
        Objects.requireNonNull(id, "Una reserva sin identificador no se puede liberar despues.");
        if (creditos < 0) {
            throw new IllegalArgumentException("Una reserva no puede ser de creditos negativos.");
        }
    }
}
