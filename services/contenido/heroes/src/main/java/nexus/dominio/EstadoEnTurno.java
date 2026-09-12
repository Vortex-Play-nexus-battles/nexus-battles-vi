package nexus.dominio;

import java.util.List;
import java.util.Map;

/**
 * Lo que la simulacion sabe del heroe al empezar un turno (HU-SIM-002). El
 * servicio no guarda estado: quien corre la simulacion (misiones) lo aporta y
 * recibe de vuelta los cursores para el turno siguiente.
 *
 * @param turno             numero del turno que empieza (1 en adelante)
 * @param poder             puntos de poder actuales del heroe
 * @param vida              vida actual del heroe
 * @param turnoDeUltimoUso  por nombre de accion, el turno en que se uso por
 *                          ultima vez (para el periodo de espera, HU-HER-007)
 * @param cursores          por rotacion, el indice del paso que le toca; null
 *                          o vacio = todas empiezan por su primer paso
 */
public record EstadoEnTurno(
        int turno,
        int poder,
        int vida,
        Map<String, Integer> turnoDeUltimoUso,
        List<Integer> cursores) {

    public EstadoEnTurno {
        turnoDeUltimoUso = turnoDeUltimoUso == null ? Map.of() : Map.copyOf(turnoDeUltimoUso);
        cursores = cursores == null ? List.of() : List.copyOf(cursores);
    }

    /** El cursor de la rotacion en esa posicion, o 0 si no se informo. */
    public int cursorDe(int indiceDeRotacion) {
        return indiceDeRotacion < cursores.size() ? cursores.get(indiceDeRotacion) : 0;
    }
}
