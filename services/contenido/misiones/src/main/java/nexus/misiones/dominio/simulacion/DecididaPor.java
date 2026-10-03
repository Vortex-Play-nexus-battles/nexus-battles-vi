package nexus.misiones.dominio.simulacion;

/**
 * Quien tomo la decision de un turno (HU-SIM-008): la regla de rotaciones de
 * heroes (7.8.5) o el modelo de IA propio. Queda en el evento de combate para
 * poder medir despues al modelo contra la regla.
 */
public enum DecididaPor {
    REGLA,
    MODELO
}
