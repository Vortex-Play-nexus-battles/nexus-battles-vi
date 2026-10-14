package nexus.misiones.dominio;

import java.util.List;
import java.util.Objects;

/**
 * La estrategia de combate de un enemigo para un prototipo y un tramo de nivel (7.8.6, «la IA controla a los
 * enemigos con estrategias predefinidas»; 7.8.5, hasta tres rotaciones por prioridad). Es un dato del juego que se
 * versiona en el repositorio, no una regla calculada.
 *
 * @param id         identificador estable, el que queda en el evento de combate para saber que estrategia jugo
 *                   un enemigo (p. ej. {@code mago-fuego-n4})
 * @param prototipo  el de la Tabla 7
 * @param desdeNivel el nivel en que se desbloquea la ultima habilidad que usa (1, 4 u 8): vale desde ese nivel
 *                   hasta el siguiente desbloqueo
 * @param rotaciones de la mas a la menos prioritaria, con los nombres exactos de la Tabla 7
 */
public record EstrategiaPredefinida(String id, String prototipo, int desdeNivel, List<List<String>> rotaciones) {

    public EstrategiaPredefinida {
        Objects.requireNonNull(id);
        Objects.requireNonNull(prototipo);
        rotaciones = rotaciones.stream().map(List::copyOf).toList();
    }
}
