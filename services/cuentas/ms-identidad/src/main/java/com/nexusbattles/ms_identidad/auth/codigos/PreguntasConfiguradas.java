package com.nexusbattles.ms_identidad.auth.codigos;

/**
 * La persona configuro o reemplazo sus preguntas de seguridad (B1). Al
 * confirmarse, queda en la auditoria cuantas —nunca cuales ni sus respuestas—
 * ({@link CorreosDeCuenta}).
 */
public record PreguntasConfiguradas(String afectado, int cantidad, String ip) {
}
