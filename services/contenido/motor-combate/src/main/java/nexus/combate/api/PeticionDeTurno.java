package nexus.combate.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Espejo del esquema {@code PeticionDeTurno} de {@code motor-combate.yaml} 1.2.0:
 * {@code combatiente} empieza su turno.
 *
 * @param combatiente  {@code id} de quien empieza
 * @param porEquipos   combate cooperativo; nulo es {@code false}
 * @param combatientes de 1 a 6
 * @param semilla      solo para pruebas reproducibles
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PeticionDeTurno(String combatiente, Boolean porEquipos, List<EstadoDeCombatiente> combatientes,
                              Long semilla) {
}
