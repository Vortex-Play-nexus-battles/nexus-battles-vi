package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * 7.4.9 — {@code /user/queue/chat/errores} ({@code chatbot.yaml} 1.0.0): un
 * problem details (regla 4) en un objeto plano, con los mismos campos que la
 * API ({@code type}, {@code title}, {@code status}, {@code detail},
 * {@code instance}, {@code motivo}) mas {@code reintentarEn} (los segundos de
 * {@code Retry-After} de un 429) y el {@code idCorrelacion} del mensaje.
 *
 * <p>Plano y no un {@link ProblemDetail}: el mezclador de Jackson que aplana
 * {@code properties} (donde va {@code motivo}) lo pone el convertidor HTTP de
 * Spring, no el del canal.
 */
public record ErrorDelCanal(String type, String title, int status, String detail, String instance, String motivo,
                            Long reintentarEn, String idCorrelacion) {

    /** El error que la API habria respondido, con la correlacion del mensaje. */
    static ErrorDelCanal desde(ResponseEntity<ProblemDetail> respuesta, String idCorrelacion) {
        ProblemDetail problema = respuesta.getBody();
        int estado = respuesta.getStatusCode().value();
        String reintentar = respuesta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        Object motivo = problema == null || problema.getProperties() == null
            ? null : problema.getProperties().get("motivo");
        return new ErrorDelCanal(
            problema == null || problema.getType() == null ? "about:blank" : problema.getType().toString(),
            problema == null ? null : problema.getTitle(),
            estado,
            problema == null ? null : problema.getDetail(),
            DestinosDelCanal.ENVIAR_MENSAJE,
            motivo == null ? null : motivo.toString(),
            reintentar == null ? null : Long.valueOf(reintentar),
            idCorrelacion);
    }
}
