package nexus.combate.reglas;

import java.util.List;
import java.util.Objects;

/**
 * El comienzo del turno de un combatiente.
 *
 * @param combatiente  {@code id} de quien empieza
 * @param porEquipos   combate cooperativo por equipos
 * @param combatientes todos los de la partida
 */
public record SolicitudDeTurno(String combatiente, boolean porEquipos, List<Contendiente> combatientes) {

    public SolicitudDeTurno {
        if (combatiente == null || combatiente.isBlank()) {
            throw new IllegalArgumentException("Hace falta quien empieza el turno.");
        }
        combatientes = List.copyOf(Objects.requireNonNull(combatientes, "Hacen falta los combatientes."));
    }
}
