package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Recompensa por jugar que el libro de creditos acredito a un participante —
 * HU-JUE-012 (RF-JUE-012).
 *
 * <p>Espejo de una entrada de {@code recompensa} en el mensaje
 * {@code partida.finalizada} del AsyncAPI. Distinta de
 * {@link RepartoDeCreditos}, que es el neto de la <b>apuesta</b>: aqui va lo
 * que se gana por el hecho de jugar (2 al ganador de un uno contra uno, 4 al
 * ganador de una grupal, 1 por participar), y lo decide ms-finanzas, no este
 * servicio. Por eso no hay constantes: se anuncia lo que el libro respondio.
 *
 * @param idJugador participante humano
 * @param creditos  cuantos creditos le acredito el libro (positivo)
 * @param ganador   si los recibio por ganar o por participar
 * @param cofre     cofre entregado con esos creditos (HU-JUE-013), o nulo
 */
public record CreditoPorPartida(UUID idJugador, int creditos, boolean ganador, UUID cofre) {

    public CreditoPorPartida {
        Objects.requireNonNull(idJugador, "Una recompensa sin jugador no se puede anunciar.");
        if (creditos < 0) {
            throw new IllegalArgumentException("La recompensa por jugar nunca resta creditos.");
        }
    }
}
