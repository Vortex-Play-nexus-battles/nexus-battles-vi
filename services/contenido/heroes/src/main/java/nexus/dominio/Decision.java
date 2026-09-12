package nexus.dominio;

import java.util.List;

/**
 * Lo que la IA ejecuta en el turno (HU-SIM-002): la accion elegida, de que
 * rotacion salio (null cuando fue el ataque basico de respaldo), cuanto poder
 * consume, por que se descarto cada rotacion evaluada antes, y los cursores
 * con que la simulacion debe llamar en el turno siguiente.
 */
public record Decision(
        String accion,
        Integer rotacion,
        int costoDePoder,
        List<Evaluacion> evaluaciones,
        List<Integer> cursoresSiguientes) {

    public Decision {
        evaluaciones = List.copyOf(evaluaciones);
        cursoresSiguientes = List.copyOf(cursoresSiguientes);
    }

    /** Por que una rotacion se ejecuto o se descarto; razon es null cuando es viable. */
    public record Evaluacion(int rotacion, String paso, boolean viable, String razon) {
    }
}
