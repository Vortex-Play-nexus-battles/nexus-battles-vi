package nexus.misiones.dominio.simulacion;

import java.util.List;

/** Lo que pasa al EMPEZAR el turno de un combatiente: protecciones que terminan, efectos por turno y +2 de poder. */
public record ResultadoDeTurno(String combatiente, List<Suceso> sucesos, List<Combatiente> combatientes) {

    public ResultadoDeTurno {
        sucesos = sucesos == null ? List.of() : List.copyOf(sucesos);
        combatientes = List.copyOf(combatientes);
    }
}
