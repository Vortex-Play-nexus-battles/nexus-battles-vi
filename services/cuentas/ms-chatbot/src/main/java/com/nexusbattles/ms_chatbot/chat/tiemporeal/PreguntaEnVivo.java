package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.regex.Pattern;

/**
 * 7.4.9 — una pregunta que llego por {@code /app/chat/mensajes}
 * ({@code EnviarMensajeEnVivo} de {@code chatbot.yaml} 1.0.0): el
 * {@code EnviarMensaje} de la API mas {@code idCorrelacion}.
 *
 * <p>Se lee a mano y no con la conversion de Spring para poder contestar
 * SIEMPRE: un JSON roto o un campo con otra forma tiene que volver por
 * {@code errores} como un 400 de la API (con la correlacion, si se pudo
 * leer), no perderse en la bitacora del servidor mientras la ventana espera.
 * {@code adjuntoUrl} (obsoleto desde 1.3.0) y cualquier otro campo se ignoran.
 *
 * @param contenido     la pregunta, tal cual (ChatService la revisa)
 * @param vista         la seccion del jugador, o null
 * @param idCorrelacion la del cliente, o null
 */
record PreguntaEnVivo(String contenido, VistaDelChat vista, String idCorrelacion) {

    /** Mismo tope que {@code EnviarMensajeRequest} ({@code @Size(max = 4000)}). */
    static final int MAXIMO_DE_CARACTERES = 4000;

    static final Pattern FORMA_DE_CORRELACION = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    /** La pregunta no tiene la forma del contrato: 400, con la correlacion si se leyo. */
    static final class Invalida extends RuntimeException {

        private final transient String idCorrelacion;

        Invalida(String detalle, String idCorrelacion) {
            super(detalle);
            this.idCorrelacion = idCorrelacion;
        }

        String idCorrelacion() {
            return idCorrelacion;
        }
    }

    static PreguntaEnVivo leer(byte[] cuerpo, ObjectMapper json) {
        JsonNode raiz;
        try {
            raiz = cuerpo == null ? null : json.readTree(cuerpo);
        } catch (JacksonException noEsJson) {
            throw new Invalida("El mensaje no es un JSON valido.", null);
        }
        if (raiz == null || !raiz.isObject()) {
            throw new Invalida("El mensaje tiene que ser un objeto JSON con «contenido».", null);
        }
        String idCorrelacion = correlacionDe(raiz.get("idCorrelacion"));

        JsonNode contenido = raiz.get("contenido");
        if (contenido == null || !contenido.isString() || contenido.stringValue().isBlank()) {
            throw new Invalida("El mensaje esta vacio.", idCorrelacion);
        }
        if (contenido.stringValue().length() > MAXIMO_DE_CARACTERES) {
            throw new Invalida("El mensaje supera los " + MAXIMO_DE_CARACTERES + " caracteres.", idCorrelacion);
        }
        return new PreguntaEnVivo(contenido.stringValue(), vistaDe(raiz.get("vista"), idCorrelacion), idCorrelacion);
    }

    private static String correlacionDe(JsonNode nodo) {
        if (nodo == null || nodo.isNull()) {
            return null;
        }
        if (!nodo.isString() || !FORMA_DE_CORRELACION.matcher(nodo.stringValue()).matches()) {
            // No se devuelve lo que llego: no tiene la forma pedida.
            throw new Invalida("idCorrelacion tiene que tener de 1 a 64 caracteres [A-Za-z0-9_-].", null);
        }
        return nodo.stringValue();
    }

    private static VistaDelChat vistaDe(JsonNode nodo, String idCorrelacion) {
        if (nodo == null || nodo.isNull()) {
            return null;
        }
        if (nodo.isString()) {
            for (VistaDelChat vista : VistaDelChat.values()) {
                if (vista.name().equals(nodo.stringValue())) {
                    return vista;
                }
            }
        }
        throw new Invalida("La vista no es ninguna de las secciones del chat.", idCorrelacion);
    }
}
