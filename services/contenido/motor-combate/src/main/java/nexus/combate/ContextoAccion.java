package nexus.combate;

import java.util.Objects;

/**
 * Informacion minima del combate y de la definicion de la accion necesaria
 * para decidir si un objetivo aliado esta protegido.
 */
public record ContextoAccion(
        boolean combateCooperativo,
        RelacionObjetivo relacionObjetivo,
        boolean permiteAfectarAliados) {

    public ContextoAccion {
        Objects.requireNonNull(relacionObjetivo, "relacionObjetivo no puede ser nula");
    }

    public boolean protegeACompanero() {
        return combateCooperativo && relacionObjetivo == RelacionObjetivo.MISMO_EQUIPO
                && !permiteAfectarAliados;
    }
}
