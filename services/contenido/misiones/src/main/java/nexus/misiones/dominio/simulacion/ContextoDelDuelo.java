package nexus.misiones.dominio.simulacion;

/**
 * Lo que el decisor puede saber del duelo ademas de su propio turno: el estado
 * de los dos combatientes al decidir (el mismo que queda en el evento como
 * {@code antes}) y quien es el oponente. La regla de heroes no lo necesita; el
 * modelo de IA si (HU-SIM-008), por eso es un dato aparte y opcional.
 *
 * @param propio   vida, poder, recargas y efectos de quien decide
 * @param oponente lo mismo, del contrario
 */
public record ContextoDelDuelo(EventoDeCombate.EstadoDeCombatiente propio,
                               EventoDeCombate.EstadoDeCombatiente oponente,
                               String prototipoDelOponente, int nivelDelOponente) {
}
