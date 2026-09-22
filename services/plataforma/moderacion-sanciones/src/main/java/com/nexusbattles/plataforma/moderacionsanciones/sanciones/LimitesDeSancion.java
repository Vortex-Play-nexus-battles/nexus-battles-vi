package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import java.time.Duration;

/**
 * Los limites que HU-ADM-001 vuelve configurables desde admin-parametros
 * (CA-04): rango de la suspension (D-20) y plazo de apelacion. Se leen por
 * API en cada uso, nunca de la base de otro servicio (regla 7).
 */
public interface LimitesDeSancion {

    Duration suspensionMinima();

    Duration suspensionMaxima();

    Duration plazoDeApelacion();

    /** Valores fijos: los de las variables de entorno, o los de una prueba. */
    record Fijos(Duration suspensionMinima, Duration suspensionMaxima, Duration plazoDeApelacion)
            implements LimitesDeSancion {
        public static Fijos de(long minimaHoras, long maximaDias, long plazoDias) {
            return new Fijos(Duration.ofHours(minimaHoras), Duration.ofDays(maximaDias), Duration.ofDays(plazoDias));
        }
    }
}
