package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Un {@link AcreditarRecompensa} de verdad, cableado a almacenes vacios y a un
 * libro en memoria: para las pruebas de combate que no miran la recompensa,
 * donde tiene que funcionar sin estorbar y no ser un doble que finja.
 */
public final class RecompensaSinLibro {

    private RecompensaSinLibro() {
    }

    public static AcreditarRecompensa nueva() {
        return new AcreditarRecompensa(new RepositorioDeSalasEnMemoria(),
                new RepositorioDeRecompensasEnMemoria(), new AcreditadorEnMemoria(),
                new SancionesEnMemoria(),
                Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC));
    }
}
