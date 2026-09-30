package com.nexusbattles.ms_subastas.reglas;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Las reglas de 7.7 que dependen de una decision de administracion, tal como
 * estan ahora mismo.
 *
 * @param incrementoMinimo            el de {@code subastas.incremento-minimo}; nulo mientras el
 *                                    PO no lo fije (o el catalogo no responda). Nunca se inventa.
 * @param maxSubastasActivasPorJugador publicaciones activas simultaneas por vendedor (7.7.10)
 * @param maxPujasActivasPorJugador   pujas activas simultaneas por jugador (7.7.10)
 * @param intervaloMinimoSegundos     entre pujas consecutivas del mismo jugador en una subasta (7.7.10)
 * @param alVencerPendientes          que pasa con lo que nadie recoge a tiempo (7.7.9, decision del PO)
 */
public record ReglasVigentes(BigDecimal incrementoMinimo,
                             int maxSubastasActivasPorJugador,
                             int maxPujasActivasPorJugador,
                             int intervaloMinimoSegundos,
                             PoliticaAlVencer alVencerPendientes) {

    public ReglasVigentes {
        Objects.requireNonNull(alVencerPendientes, "alVencerPendientes");
        if (incrementoMinimo != null && incrementoMinimo.signum() <= 0) {
            throw new IllegalArgumentException("el incremento minimo tiene que ser positivo");
        }
    }

    public Optional<BigDecimal> incremento() {
        return Optional.ofNullable(incrementoMinimo);
    }
}
