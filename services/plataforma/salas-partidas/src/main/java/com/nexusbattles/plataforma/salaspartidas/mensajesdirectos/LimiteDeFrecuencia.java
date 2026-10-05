package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cuantos mensajes privados puede mandar un jugador seguidos — B6.
 *
 * <p>El chat no tenia ninguno (auditoria de septiembre). Un mensaje privado
 * llega a una persona concreta y, si no esta conectada, a su bandeja: sin
 * limite, cualquiera podia inundar la de otro.
 */
public interface LimiteDeFrecuencia {

    /**
     * Cuenta un intento de ese remitente.
     *
     * @return vacio si el intento cabe en el limite (y queda contado), o cuanto
     *     falta para que quepa otro (y no se cuenta)
     */
    Optional<Duration> registrar(UUID remitente);
}
