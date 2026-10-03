package nexus.misiones.dominio.simulacion;

import java.util.List;
import java.util.Map;

/**
 * Lo que el decisor necesita saber del combatiente al empezar su turno: el
 * {@code EstadoEnTurno} de heroes.yaml mas la estrategia.
 *
 * @param turno            numero de ronda dentro del combate, desde 1
 * @param turnoDeUltimoUso por habilidad, la ronda en que se uso (recarga)
 * @param cursores         por rotacion, el paso que le toca (vacio = el primero)
 * @param contexto         el estado de los dos y quien es el oponente, o nulo si quien arma el turno no lo sabe;
 *                         solo lo usa el modelo de IA (HU-SIM-008), heroes no lo recibe
 */
public record TurnoParaDecidir(
        String prototipo,
        int nivel,
        List<List<String>> rotaciones,
        int turno,
        int poder,
        int vida,
        Map<String, Integer> turnoDeUltimoUso,
        List<Integer> cursores,
        ContextoDelDuelo contexto) {

    public TurnoParaDecidir {
        rotaciones = rotaciones == null ? List.of() : rotaciones;
        turnoDeUltimoUso = turnoDeUltimoUso == null ? Map.of() : Map.copyOf(turnoDeUltimoUso);
        cursores = cursores == null ? List.of() : List.copyOf(cursores);
    }

    /** Un turno sin contexto del duelo: lo que necesita la regla. */
    public TurnoParaDecidir(String prototipo, int nivel, List<List<String>> rotaciones, int turno, int poder,
                            int vida, Map<String, Integer> turnoDeUltimoUso, List<Integer> cursores) {
        this(prototipo, nivel, rotaciones, turno, poder, vida, turnoDeUltimoUso, cursores, null);
    }
}
