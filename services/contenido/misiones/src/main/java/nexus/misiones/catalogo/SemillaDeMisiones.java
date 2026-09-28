package nexus.misiones.catalogo;

import java.util.List;
import nexus.misiones.dominio.EpicaDeTabla20;
import nexus.misiones.dominio.Mision;

/**
 * Un archivo de semilla tal como esta en {@code src/main/resources/semilla}.
 *
 * @param version version del contenido, para saber que se publico
 * @param notas   que es del documento y que es provisional, en palabras: el
 *                JSON no admite comentarios y esto no debe perderse
 * @param tabla20 filas de la Tabla 20 (solo en la semilla del documento)
 * @param misiones las misiones publicadas
 */
public record SemillaDeMisiones(String version, List<String> notas, List<EpicaDeTabla20> tabla20,
                                List<Mision> misiones) {

    public SemillaDeMisiones {
        notas = notas == null ? List.of() : List.copyOf(notas);
        tabla20 = tabla20 == null ? List.of() : List.copyOf(tabla20);
        misiones = misiones == null ? List.of() : List.copyOf(misiones);
    }
}
