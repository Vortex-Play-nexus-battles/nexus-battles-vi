package com.nexusbattles.ms_identidad.sanciones;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code ProyeccionDeSancion} de ms-identidad-admin.yaml: el estado de acceso
 * que una sancion de moderacion-sanciones produce sobre la cuenta.
 *
 * @param estado    ACTIVO, SUSPENDIDO o BANEADO
 * @param hasta     fin de la suspension; obligatorio con SUSPENDIDO, ignorado en los demas
 * @param sancionId la sancion que produce el estado (clave de idempotencia)
 * @param motivo    informativo; no se guarda
 */
public record ProyeccionDeSancionRequest(String estado, OffsetDateTime hasta, UUID sancionId, String motivo) {
}
