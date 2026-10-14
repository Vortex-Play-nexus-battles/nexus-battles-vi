package nexus.combate;

import java.util.List;

public record ResultadoPerdidaEquipo(List<PerdidaEquipoAsignada> asignaciones) {

    public ResultadoPerdidaEquipo {
        asignaciones = List.copyOf(asignaciones);
    }
}
