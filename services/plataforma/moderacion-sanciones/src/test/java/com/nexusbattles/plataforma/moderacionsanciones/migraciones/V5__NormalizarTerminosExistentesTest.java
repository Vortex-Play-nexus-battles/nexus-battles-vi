package com.nexusbattles.plataforma.moderacionsanciones.migraciones;

import com.nexusbattles.plataforma.moderacionsanciones.listanegra.ModoDeCoincidencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** La decision de V5 sin base de datos; la parte JDBC la prueba MigracionListaNegraIT. */
@DisplayName("V5 · forma y modo de los terminos anteriores a la 2.0.0")
class V5__NormalizarTerminosExistentesTest {

    @Test
    @DisplayName("cada fila con su forma y su modo; la segunda que choca se apaga con su id; una vacia tambien")
    void planificar() {
        Set<String> ocupadas = new HashSet<>(Set.of("batman"));
        List<V5__NormalizarTerminosExistentes.Asignacion> plan = V5__NormalizarTerminosExistentes.planificar(List.of(
                new V5__NormalizarTerminosExistentes.FilaExistente(1, "malapalabra"),
                new V5__NormalizarTerminosExistentes.FilaExistente(2, "Spider-Man"),
                new V5__NormalizarTerminosExistentes.FilaExistente(3, "spiderman"),
                new V5__NormalizarTerminosExistentes.FilaExistente(4, "!!!"),
                new V5__NormalizarTerminosExistentes.FilaExistente(5, "culo"),
                new V5__NormalizarTerminosExistentes.FilaExistente(6, "BATMAN")), ocupadas);

        assertThat(plan).containsExactly(
                new V5__NormalizarTerminosExistentes.Asignacion(1, "malapalabra", ModoDeCoincidencia.SUBCADENA, false),
                new V5__NormalizarTerminosExistentes.Asignacion(2, "spiderman", ModoDeCoincidencia.SUBCADENA, false),
                new V5__NormalizarTerminosExistentes.Asignacion(3, "spiderman#3", ModoDeCoincidencia.SUBCADENA, true),
                new V5__NormalizarTerminosExistentes.Asignacion(4, "#4", ModoDeCoincidencia.PALABRA, true),
                new V5__NormalizarTerminosExistentes.Asignacion(5, "culo", ModoDeCoincidencia.PALABRA, false),
                new V5__NormalizarTerminosExistentes.Asignacion(6, "batman#6", ModoDeCoincidencia.SUBCADENA, true));
        assertThat(ocupadas).contains("malapalabra", "spiderman", "culo", "batman");
    }
}
