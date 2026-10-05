package com.nexusbattles.plataforma.torneos.torneo;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Cuando se vuelve a intentar una operacion que fallo por algo pasajero, y
 * cuando se deja de intentar.
 *
 * <p>Espera exponencial ({@code base, 2*base, 4*base...}) con techo, para no
 * martillar a un proveedor caido; tras {@code intentosMaximos} la operacion
 * queda FALLIDA para que la revise un administrador en vez de reintentarse
 * para siempre en silencio. {@code plazoDeBloqueo} es lo que puede tardar una
 * ejecucion antes de que otra la de por muerta y la retome: tiene que ser mayor
 * que la suma de los tiempos de espera HTTP de una operacion.
 */
public record PoliticaDeReintentos(Duration esperaBase, Duration esperaMaxima, int intentosMaximos,
                                   Duration plazoDeBloqueo) {

    public PoliticaDeReintentos {
        Objects.requireNonNull(esperaBase);
        Objects.requireNonNull(esperaMaxima);
        Objects.requireNonNull(plazoDeBloqueo);
        if (intentosMaximos < 1) {
            throw new IllegalArgumentException("hace falta al menos un intento");
        }
    }

    /** Hora del siguiente intento tras {@code intentos} intentos fallidos. */
    public OffsetDateTime siguiente(int intentos, OffsetDateTime ahora) {
        int exponente = Math.max(0, Math.min(intentos - 1, 20));
        Duration espera = esperaBase.multipliedBy(1L << exponente);
        if (espera.compareTo(esperaMaxima) > 0) {
            espera = esperaMaxima;
        }
        return ahora.plus(espera);
    }

    public boolean agotada(int intentos) {
        return intentos >= intentosMaximos;
    }
}
