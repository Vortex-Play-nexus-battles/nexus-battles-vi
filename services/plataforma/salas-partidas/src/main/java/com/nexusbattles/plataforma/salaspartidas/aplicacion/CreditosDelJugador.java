package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosInsuficientes;

import java.util.UUID;

/**
 * Puerto de salida hacia el modulo de creditos.
 *
 * <p>RF-JUE-014: los creditos puestos en juego quedan <b>comprometidos</b> al
 * crear la sala. Comprometer es reservar, no mirar: si este puerto solo
 * consultara el saldo, el mismo jugador podria crear dos salas de 400 creditos
 * teniendo 500, porque las dos consultas pasarian antes de que ninguna
 * descontara.
 *
 * <p>Por eso {@link #reservar} <b>comprueba y descuenta en una sola operacion
 * atomica del lado del proveedor</b>. Este servicio no es dueno del saldo, no lo
 * guarda y no lo calcula.
 *
 * <p><b>Adaptador pendiente.</b> No existe contrato del modulo de creditos:
 * {@code ms-finanzas} esta vacio y {@code contracts/} no tiene nada suyo. El
 * puerto se queda sin implementacion de produccion a proposito —un adaptador
 * inventado seria peor que ninguno— y el caso de uso se prueba contra un doble,
 * que es lo que manda el Project Charter cuando el proveedor no existe.
 */
public interface CreditosDelJugador {

    /**
     * Reserva creditos del jugador para una sala.
     *
     * <p>Debe ser atomica: comprobar y descontar sin hueco entre medias.
     *
     * @param idJugador jugador que compromete sus creditos
     * @param creditos  cuantos, nunca negativo
     * @param idSala    sala a la que queda ligada la reserva
     * @return la reserva creada
     * @throws CreditosInsuficientes si el saldo no alcanza
     */
    ReservaDeCreditos reservar(UUID idJugador, int creditos, UUID idSala);

    /**
     * Devuelve una reserva al jugador.
     *
     * <p>Se llama cuando la sala no llega a existir —un fallo al guardarla— o
     * cuando se cancela. Debe ser idempotente: liberar dos veces la misma
     * reserva no puede devolver los creditos dos veces.
     */
    void liberar(UUID idReserva);
}
