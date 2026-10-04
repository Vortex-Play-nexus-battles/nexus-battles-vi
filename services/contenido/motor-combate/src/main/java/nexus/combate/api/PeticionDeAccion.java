package nexus.combate.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Espejo del esquema {@code PeticionDeAccion} de {@code motor-combate.yaml} 1.2.0:
 * el ejecutor juega {@code accion} sobre {@code objetivo}.
 *
 * @param accion       {@code ATAQUE_BASICO}, {@code SANACION_BASICA}, una accion de
 *                     la Tabla 7, una epica o {@code DECISION_DE_LA_MAQUINA}
 * @param ejecutor     {@code id} de quien actua
 * @param objetivo     {@code id} del objetivo; opcional si solo hay uno posible
 * @param porEquipos   combate cooperativo (§6.1.3); nulo es {@code false}
 * @param combatientes de 2 a 6
 * @param semilla      solo para pruebas reproducibles
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PeticionDeAccion(String accion, String ejecutor, String objetivo, Boolean porEquipos,
                               List<EstadoDeCombatiente> combatientes, Long semilla) {
}
