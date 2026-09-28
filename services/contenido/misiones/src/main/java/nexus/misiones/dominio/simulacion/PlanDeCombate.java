package nexus.misiones.dominio.simulacion;

import java.util.ArrayList;
import java.util.List;

/**
 * El orden de los encuentros: los enemigos regulares en el orden de la mision,
 * los Master que aparecieron intercalados al azar entre ellos («aparicion
 * aleatoria durante la mision», 7.8.4) y el jefe siempre al final («enemigo
 * principal de mayor dificultad al final de la mision», 7.8.3).
 */
public final class PlanDeCombate {

    private PlanDeCombate() {
    }

    public static List<Rival> armar(List<Rival> regulares, List<Rival> masters, Rival jefe, Azar azar) {
        List<Rival> orden = new ArrayList<>(regulares);
        for (Rival master : masters) {
            orden.add(azar.entre(0, orden.size()), master);
        }
        if (jefe != null) {
            orden.add(jefe);
        }
        return List.copyOf(orden);
    }
}
