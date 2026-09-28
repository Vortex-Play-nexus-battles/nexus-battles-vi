package nexus.misiones.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * La estrategia de un heroe, guardada por su jugador (7.8.12, «configuraciones
 * de rotaciones guardadas»; heroes.yaml: «quien guarda la configuracion es el
 * modulo de misiones»). Se guarda ya validada por heroes, con los nombres
 * exactos de la Tabla 7 que devolvio su veredicto.
 */
public record EstrategiaGuardada(
        String jugadorUid,
        String heroeId,
        String prototipo,
        int nivel,
        List<List<String>> rotaciones,
        Instant actualizadaEn) {

    public EstrategiaGuardada {
        Objects.requireNonNull(jugadorUid);
        Objects.requireNonNull(heroeId);
        Objects.requireNonNull(prototipo);
        rotaciones = rotaciones == null ? List.of() : rotaciones.stream().map(List::copyOf).toList();
        Objects.requireNonNull(actualizadaEn);
    }
}
