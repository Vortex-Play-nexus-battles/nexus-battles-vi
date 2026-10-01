package nexus.misiones.aplicacion;

import java.util.List;
import nexus.misiones.dominio.simulacion.OrigenDeEstrategia;

/**
 * La estrategia de la IA de un enemigo cuya mision no trae las rotaciones escritas (7.8.6, «la IA controla a los
 * enemigos con estrategias predefinidas»). Si la mision las escribe, estas ganan y no se pregunta aqui: esta interfaz
 * solo decide que hacer cuando no hay. Las implementaciones: {@link EstrategiasPredefinidas} (HU-SIM-004, las
 * estrategias de la semilla por prototipo y nivel, con la heuristica de respaldo) y
 * {@link RotacionesPorDefectoDeEnemigos} (la heuristica).
 */
@FunctionalInterface
public interface EstrategiaDeEnemigos {

    /**
     * @return las rotaciones por orden de prioridad, con los nombres exactos de
     *         la Tabla 7; vacia = ataque basico siempre
     */
    List<List<String>> porDefecto(String prototipo, int nivel);

    /**
     * La estrategia que juega un enemigo sin rotaciones escritas en la mision, con su origen y su id para dejar
     * constancia en el evento de combate. Por omision, la heuristica.
     */
    default Elegida elegir(String prototipo, int nivel) {
        return new Elegida(OrigenDeEstrategia.HEURISTICA, null, porDefecto(prototipo, nivel));
    }

    /**
     * @param origen    PREDEFINIDA o HEURISTICA (MISION no pasa por aqui: la rotacion de la mision se usa tal cual)
     * @param id        el de la estrategia predefinida; nulo en la heuristica
     * @param rotaciones por orden de prioridad; vacia = ataque basico siempre
     */
    record Elegida(OrigenDeEstrategia origen, String id, List<List<String>> rotaciones) {
    }
}
