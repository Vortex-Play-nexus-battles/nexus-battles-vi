package com.nexusbattles.plataforma.salaspartidas.chat;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cuantos mensajes seguidos admite el chat de un mismo autor — auditoria de DEV
 * del 30-sep: el chat general y el de sala no tenian ninguno (los privados si,
 * desde B6). El adaptador es el mismo de los privados,
 * {@code LimiteDeFrecuenciaEnMemoria}, con sus propios valores (D-37).
 */
@FunctionalInterface
public interface LimiteDeEnvios {

    /** Sin limite: lo usan las pruebas que no lo miran. */
    LimiteDeEnvios SIN_LIMITE = autor -> Optional.empty();

    /**
     * Cuenta un intento de ese autor.
     *
     * @return vacio si cabe (y queda contado), o cuanto falta para que quepa otro
     */
    Optional<Duration> registrar(UUID autor);
}
