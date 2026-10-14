package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

/**
 * Las causales de sancion que el sistema documenta — §7.3.2 «Causales de
 * sancion» del documento oficial, las ocho y en su orden (SA-4).
 *
 * <p>Es una lista cerrada porque la cierra el requisito: ampliarla es cambiar
 * el requisito, no la API. La causal dice <i>por que regla</i> se sanciona; el
 * {@code motivo} de la sancion sigue siendo el texto con el hecho concreto
 * («insulto a otro jugador en el chat de la sala 12»), y los dos llegan al
 * aviso y al correo del sancionado (§7.3.7: «motivo especifico» y
 * «referencias a las politicas violadas»).
 */
public enum Causal {

    LENGUAJE_OFENSIVO("Uso de lenguaje ofensivo o abusivo en comentarios o chat"),
    ACOSO("Acoso a otros jugadores"),
    FRAUDE("Intentos de fraude o manipulación del sistema"),
    SOFTWARE_NO_AUTORIZADO("Uso de software no autorizado o trampas"),
    SUPLANTACION_DE_IDENTIDAD("Suplantación de identidad"),
    VIOLACION_DE_PRIVACIDAD("Violación de políticas de privacidad"),
    INCUMPLIMIENTO_DE_TERMINOS("Incumplimiento de términos y condiciones del servicio"),
    TERMINACION_DE_RELACION_LEGAL("Terminación de la relación legal entre las partes");

    private final String etiqueta;

    Causal(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** El texto del documento oficial, como se lee en el aviso y en el correo. */
    public String etiqueta() {
        return etiqueta;
    }
}
