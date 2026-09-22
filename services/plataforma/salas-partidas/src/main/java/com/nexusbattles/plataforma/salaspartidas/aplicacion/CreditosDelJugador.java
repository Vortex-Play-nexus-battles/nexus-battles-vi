package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosInsuficientes;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosNoDisponibles;

import java.util.UUID;

/**
 * Puerto de salida hacia el libro de creditos — RF-JUE-014, HU-JUE-014.
 *
 * <p>Los creditos puestos en juego quedan <b>comprometidos</b> al crear la sala
 * o al entrar en ella. Comprometer es reservar, no mirar: si este puerto solo
 * consultara el saldo, el mismo jugador podria crear dos salas de 400 creditos
 * teniendo 500, porque las dos consultas pasarian antes de que ninguna
 * descontara.
 *
 * <p>Por eso {@link #reservar} <b>comprueba y aparta en una sola operacion
 * atomica del lado del proveedor</b>. Este servicio no es dueno del saldo, no lo
 * guarda y no lo calcula.
 *
 * <p>El proveedor es {@code ms-finanzas} (equipo de Cuentas), por el contrato
 * {@code contracts/openapi/creditos.yaml}. Las tres operaciones son idempotentes
 * de su lado, y eso es lo que permite reintentar una liquidacion a medias sin
 * mover creditos dos veces.
 */
public interface CreditosDelJugador {

    /**
     * Reserva creditos del jugador para una sala.
     *
     * <p>Debe ser atomica: comprobar y descontar sin hueco entre medias.
     *
     * @param idJugador jugador que compromete sus creditos
     * @param creditos  cuantos, mayor que cero
     * @param idSala    sala a la que queda ligada la reserva
     * @param ingreso   marca que distingue dos ingresos del mismo jugador a la
     *                  misma sala (la version de la sala al entrar). Repetir la
     *                  misma marca devuelve la misma reserva: es la clave de
     *                  idempotencia, y por eso quien vuelve a entrar despues de
     *                  irse —con la sala ya cambiada— consigue una reserva nueva
     *                  y no la que ya se le devolvio.
     * @return la reserva creada
     * @throws CreditosInsuficientes si el saldo disponible no alcanza
     * @throws CreditosNoDisponibles si el libro de creditos no responde
     */
    ReservaDeCreditos reservar(UUID idJugador, int creditos, UUID idSala, long ingreso);

    /**
     * Devuelve una reserva al jugador.
     *
     * <p>Se llama cuando la sala no llega a existir —un fallo al guardarla—,
     * cuando el jugador se va antes de empezar, cuando se cancela la sala o
     * cuando la partida acaba en empate. Idempotente: liberar dos veces la misma
     * reserva no devuelve los creditos dos veces.
     *
     * @throws CreditosNoDisponibles si el libro de creditos no responde
     */
    void liberar(UUID idReserva);

    /**
     * Cobra una reserva y acredita su importe al beneficiario — la liquidacion
     * de la apuesta al terminar la partida.
     *
     * <p>Idempotente: una reserva ya cobrada no se cobra dos veces. Consumir
     * una reserva que ya se libero es un error del proveedor (409) que se
     * propaga como {@link CreditosNoDisponibles}: significa que este servicio
     * y el libro no estan de acuerdo, y eso hay que verlo, no taparlo.
     *
     * @param idReserva     reserva del jugador que pierde lo apostado
     * @param idBeneficiario quien se lo lleva
     * @throws CreditosNoDisponibles si el libro de creditos no responde o rechaza
     */
    void consumir(UUID idReserva, UUID idBeneficiario);
}
