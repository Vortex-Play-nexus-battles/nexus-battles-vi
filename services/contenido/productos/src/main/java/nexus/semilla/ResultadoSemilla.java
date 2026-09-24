package nexus.semilla;

import java.util.List;

/** Lo que hizo una corrida de la semilla, para la bitacora y las pruebas. */
public record ResultadoSemilla(
        boolean habilitada,
        List<String> insertados,
        List<String> existentes,
        List<String> rechazados) {

    public static ResultadoSemilla deshabilitada() {
        return new ResultadoSemilla(false, List.of(), List.of(), List.of());
    }
}
