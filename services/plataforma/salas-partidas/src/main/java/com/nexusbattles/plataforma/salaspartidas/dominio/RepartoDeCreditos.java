package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Resultado economico de la partida para un participante — HU-JUE-014, CA-04.
 *
 * <p>Espejo de una entrada de {@code reparto} en el mensaje
 * {@code partida.finalizada} del AsyncAPI: cuantos creditos <b>netos</b> gano
 * o perdio este jugador. Negativo si perdio lo apostado, positivo si se llevo
 * lo de los demas, cero si se le devolvio lo suyo (empate, o partida contra la
 * maquina).
 *
 * <p>Solo cubre la apuesta. La recompensa por jugar (2/4 creditos al ganador,
 * 1 por participar) es de HU-JUE-012 y la calcula ms-finanzas; no se suma
 * aqui porque no es de este servicio saber cuanto vale una victoria.
 *
 * @param idJugador participante
 * @param creditos  saldo neto de la apuesta para el
 */
public record RepartoDeCreditos(UUID idJugador, int creditos) {

    public RepartoDeCreditos {
        Objects.requireNonNull(idJugador, "Un reparto sin jugador no se puede anunciar.");
    }
}
