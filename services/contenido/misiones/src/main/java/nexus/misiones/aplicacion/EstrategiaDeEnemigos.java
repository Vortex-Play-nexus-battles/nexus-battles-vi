package nexus.misiones.aplicacion;

import java.util.List;

/**
 * La estrategia de la IA de un enemigo que la mision no trae escrita (7.8.6,
 * «la IA controla a los enemigos con estrategias predefinidas»). Es el punto
 * de extension de HU-SIM-004: cuando las misiones declaren las rotaciones de
 * cada enemigo, estas ganan; esta interfaz solo decide que hacer cuando no hay.
 */
@FunctionalInterface
public interface EstrategiaDeEnemigos {

    /**
     * @return las rotaciones por orden de prioridad, con los nombres exactos de
     *         la Tabla 7; vacia = ataque basico siempre
     */
    List<List<String>> porDefecto(String prototipo, int nivel);
}
