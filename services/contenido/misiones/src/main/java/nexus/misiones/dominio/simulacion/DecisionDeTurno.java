package nexus.misiones.dominio.simulacion;

import java.util.List;

/**
 * La jugada decidida: que accion, cuanto poder cuesta y con que cursores se
 * llama en el turno siguiente ({@code DecisionDelTurno} de heroes.yaml).
 */
public record DecisionDeTurno(String accion, int costoDePoder, List<Integer> cursoresSiguientes) {

    /** Seccion 7.8.5: «ataque basico sin consumir poder». */
    public static final String ATAQUE_BASICO = "Ataque básico";

    public DecisionDeTurno {
        if (accion == null || accion.isBlank()) {
            throw new IllegalArgumentException("Una decision sin accion no es una decision.");
        }
        if (costoDePoder < 0) {
            throw new IllegalArgumentException("El costo de poder no puede ser negativo.");
        }
        cursoresSiguientes = cursoresSiguientes == null ? List.of() : List.copyOf(cursoresSiguientes);
    }

    public boolean esAtaqueBasico() {
        return ATAQUE_BASICO.equals(accion);
    }
}
