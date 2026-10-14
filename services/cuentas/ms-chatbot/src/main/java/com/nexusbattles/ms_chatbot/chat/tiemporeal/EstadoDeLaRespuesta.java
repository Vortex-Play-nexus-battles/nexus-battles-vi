package com.nexusbattles.ms_chatbot.chat.tiemporeal;

/**
 * 7.4.9 — {@code /user/queue/chat/estado} ({@code chatbot.yaml} 1.0.0): el
 * asistente acepto la pregunta y la esta respondiendo. La ventana pinta
 * «escribiendo…» con esto; tambien sirve de acuse de recibo.
 *
 * @param estado        hoy siempre {@code ESCRIBIENDO}
 * @param idCorrelacion el del mensaje, si lo traia
 */
public record EstadoDeLaRespuesta(String estado, String idCorrelacion) {

    static final String ESCRIBIENDO = "ESCRIBIENDO";

    static EstadoDeLaRespuesta escribiendo(String idCorrelacion) {
        return new EstadoDeLaRespuesta(ESCRIBIENDO, idCorrelacion);
    }
}
