package com.nexusbattles.ms_identidad.sanciones;

/**
 * Las ocho causales de sancion de §7.3.2 tal como las nombra
 * moderacion-sanciones ({@code Causal} de moderacion-sanciones-admin.yaml
 * 1.4.0), para validar en la puerta lo que el panel pide al suspender o
 * banear (SA-4).
 *
 * <p>Identidad no decide nada con ellas: solo comprueba que la causal es una
 * de las ocho antes de pedir la sancion, para responder un 400 claro en vez
 * de reenviar un cuerpo que moderacion rechazaria. La lista la cierra el
 * documento oficial; si cambiara, cambian los dos contratos a la vez.
 */
public final class CausalesDeSancion {

    /** Una de las ocho, en mayusculas, sin nada mas. */
    public static final String PATRON = "LENGUAJE_OFENSIVO|ACOSO|FRAUDE|SOFTWARE_NO_AUTORIZADO"
            + "|SUPLANTACION_DE_IDENTIDAD|VIOLACION_DE_PRIVACIDAD|INCUMPLIMIENTO_DE_TERMINOS"
            + "|TERMINACION_DE_RELACION_LEGAL";

    /** El mensaje del 400 cuando no es ninguna de ellas. */
    public static final String MENSAJE = "La causal debe ser una de las ocho de §7.3.2.";

    private CausalesDeSancion() {
    }
}
