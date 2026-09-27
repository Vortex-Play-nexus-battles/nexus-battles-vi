package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Como se ordenan los turnos al empezar — §6.1.3: «El orden de los turnos para
 * la primera ronda se determina aleatoriamente entre todos los participantes y
 * se mantiene invariable hasta la conclusion del combate».
 *
 * <p>{@link #sorteado} baraja con una SEMILLA que la partida guarda: el sorteo
 * es aleatorio de verdad —la semilla sale de {@code SecureRandom} en
 * {@code IniciarPartida}— y a la vez auditable, porque con la semilla se puede
 * repetir. Con seis participantes hay 720 ordenes posibles, muy por debajo de
 * lo que distingue un {@link Random} de 48 bits.
 *
 * <p>{@link #DE_ENTRADA} deja el orden en que entraron (anfitrion primero). No
 * lo usa ninguna partida real: existe para las pruebas que ejercitan otras
 * reglas y necesitan saber quien juega cada turno.
 *
 * @param semilla semilla del sorteo; {@code null} = orden de entrada
 */
public record OrdenDeTurnos(Long semilla) {

    /** El orden de entrada, sin sorteo. Ver la nota de la clase. */
    public static final OrdenDeTurnos DE_ENTRADA = new OrdenDeTurnos(null);

    /** El orden sorteado con esta semilla. */
    public static OrdenDeTurnos sorteado(long semilla) {
        return new OrdenDeTurnos(semilla);
    }

    /** La lista en el orden de los turnos. No toca la original. */
    public <T> List<T> aplicar(List<T> participantes) {
        List<T> copia = new ArrayList<>(participantes);
        if (semilla != null) {
            Collections.shuffle(copia, new Random(semilla));
        }
        return copia;
    }
}
