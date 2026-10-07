package nexus.misiones.ia;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nexus.misiones.dominio.simulacion.DecididaPor;
import nexus.misiones.dominio.simulacion.DecisionDeTurno;
import nexus.misiones.dominio.simulacion.DecisorDeTurno;
import nexus.misiones.dominio.simulacion.TurnoParaDecidir;

/**
 * La regla de heroes (HU-SIM-002, {@code DecisorDeRotaciones}) como doble de prueba: en cada turno la primera
 * rotacion viable —poder suficiente y recarga de un turno cumplida—, el cursor que avanza solo en la ejecutada y el
 * ataque basico de respaldo, con la rotacion de la que salio. Cuenta y recuerda las llamadas, que es lo que el
 * decorador del modelo multiplica.
 */
final class ReglaDeRotaciones implements DecisorDeTurno {

    final Map<String, Integer> costos;
    final List<TurnoParaDecidir> llamadas = new ArrayList<>();
    /** Si no es nulo, la llamada con este numero (desde 1) lanza esta excepcion. */
    int fallarEnLaLlamada;
    RuntimeException fallo;

    ReglaDeRotaciones(Map<String, Integer> costos) {
        this.costos = costos;
    }

    @Override
    public DecisionDeTurno decidir(TurnoParaDecidir turno) {
        llamadas.add(turno);
        if (fallarEnLaLlamada == llamadas.size()) {
            throw fallo;
        }
        List<Integer> cursores = new ArrayList<>();
        for (int i = 0; i < turno.rotaciones().size(); i++) {
            cursores.add(turno.cursores().size() > i ? turno.cursores().get(i) : 0);
        }
        for (int i = 0; i < turno.rotaciones().size(); i++) {
            List<String> pasos = turno.rotaciones().get(i);
            String paso = pasos.get(cursores.get(i) % pasos.size());
            int costo = DecisionDeTurno.ATAQUE_BASICO.equals(paso) ? 0 : costos.getOrDefault(paso, 0);
            Integer ultimo = turno.turnoDeUltimoUso().get(paso);
            boolean enRecarga = ultimo != null && turno.turno() < ultimo + 2;
            if (costo <= turno.poder() && !enRecarga) {
                cursores.set(i, cursores.get(i) + 1);
                return new DecisionDeTurno(paso, costo, cursores, i + 1, DecididaPor.REGLA, null, List.of());
            }
        }
        return new DecisionDeTurno(DecisionDeTurno.ATAQUE_BASICO, 0, cursores, null, DecididaPor.REGLA, null,
                List.of());
    }
}
