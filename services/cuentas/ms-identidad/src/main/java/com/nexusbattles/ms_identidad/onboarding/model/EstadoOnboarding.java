package com.nexusbattles.ms_identidad.onboarding.model;

/**
 * Estado del alta de un jugador nuevo (R17.1).
 *
 * <ul>
 *   <li>{@link #PENDIENTE}: la cuenta existe y los pasos aun no se han intentado.</li>
 *   <li>{@link #EN_PROCESO}: un trabajador tiene el turno (con fecha de caducidad,
 *       para que un proceso muerto no bloquee la cuenta para siempre).</li>
 *   <li>{@link #COMPLETO}: todos los pasos respondieron que si.</li>
 *   <li>{@link #ERROR_REINTENTABLE}: algun paso fallo; se reintentara solo los que
 *       faltan, sin repetir los hechos.</li>
 * </ul>
 */
public enum EstadoOnboarding {
    PENDIENTE,
    EN_PROCESO,
    COMPLETO,
    ERROR_REINTENTABLE
}
