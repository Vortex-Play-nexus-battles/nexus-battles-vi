package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Un {@link LiquidarApuesta} de verdad, cableado a almacenes vacios: para las
 * pruebas de combate cuyas partidas no tienen sala guardada ni apuesta, donde
 * la liquidacion tiene que ser un no-op (CA-05) y no un doble que finja.
 */
public final class LiquidacionSinApuesta {

    private LiquidacionSinApuesta() {
    }

    public static LiquidarApuesta nueva() {
        return new LiquidarApuesta(new RepositorioDeSalasEnMemoria(),
                new RepositorioDeLiquidacionesEnMemoria(), new CreditosEnMemoria(),
                Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC),
                LiquidarApuesta.SiGanaLaMaquina.LIBERAR);
    }
}
