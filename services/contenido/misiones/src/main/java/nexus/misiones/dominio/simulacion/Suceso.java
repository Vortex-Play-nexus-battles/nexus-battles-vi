package nexus.misiones.dominio.simulacion;

/**
 * Algo que paso durante una accion o al empezar un turno ({@code Evento} de
 * motor-combate.yaml): dano, sanacion, un efecto que empieza o termina, poder
 * recuperado...
 *
 * @param tipo        DANO, SANACION, EFECTO_APLICADO, DANO_POR_TURNO, PODER_RECUPERADO, VALOR_BASE, CAIDO...
 * @param combatiente a quien le paso (el id que se le mando al motor)
 * @param origen      quien lo causo, si alguien
 * @param efecto      nombre del efecto, la accion o el equipo
 */
public record Suceso(String tipo, String combatiente, String origen, String efecto, Integer cantidad) {
}
