package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.service.PreguntaParaSoporte;

// ms-chatbot.yaml 1.8.0: el asunto y el mensaje de una solicitud armada con la
// pregunta que el asistente no resolvio. Dentro de los limites del contrato
// (`AbrirTicket`: asunto 150, mensaje 2000), que tambien son los de la tabla.
//
// Se escriben como los escribiria el jugador, porque es el quien los ve en
// «Tus solicitudes»: «Le pregunte al asistente: «...» y no la entendio.»
final class SolicitudDesdeConversacion {

    static final int LARGO_MAXIMO_DEL_ASUNTO = 150;
    static final int LARGO_MAXIMO_DEL_MENSAJE = 2000;
    static final String PREFIJO_DEL_ASUNTO = "Sin respuesta del asistente: ";

    // Por debajo de esto, la pregunta recortada ya no se entiende: si el
    // comentario ocupa casi todo el mensaje, va solo el comentario (la
    // pregunta sigue en el asunto y en el contexto).
    static final int MINIMO_DE_PREGUNTA = 40;

    private static final String ANTES = "Le pregunté al asistente: «";

    private SolicitudDesdeConversacion() {
        // Utilidades sin estado.
    }

    static String asunto(PreguntaParaSoporte pregunta) {
        return PREFIJO_DEL_ASUNTO
            + recortar(enUnaLinea(pregunta.texto()), LARGO_MAXIMO_DEL_ASUNTO - PREFIJO_DEL_ASUNTO.length());
    }

    /**
     * La pregunta y lo que paso con ella y, despues, el comentario del jugador
     * si lo hay. El comentario ya llega revisado y nunca se recorta: lo que se
     * recorta, si no cabe, es la pregunta.
     */
    static String mensaje(PreguntaParaSoporte pregunta, String comentario) {
        String despues = switch (pregunta.motivo()) {
            case NO_LA_ENTENDIO -> "» y no la entendió.";
            case NO_LE_SIRVIO -> "» y su respuesta no me sirvió.";
            case ULTIMA_PREGUNTA -> "» y necesito que me ayude una persona del equipo.";
        };
        String extra = comentario == null || comentario.isBlank() ? "" : "\n\n" + comentario.strip();
        int cabe = LARGO_MAXIMO_DEL_MENSAJE - ANTES.length() - despues.length() - extra.length();
        if (cabe < MINIMO_DE_PREGUNTA) {
            return recortar(comentario.strip(), LARGO_MAXIMO_DEL_MENSAJE);
        }
        return ANTES + recortar(enUnaLinea(pregunta.texto()), cabe) + despues + extra;
    }

    static String enUnaLinea(String texto) {
        return texto == null ? "" : texto.replaceAll("\\s+", " ").strip();
    }

    // Como mucho `maximo` caracteres, con «…» al final si se corto.
    static String recortar(String texto, int maximo) {
        if (texto.length() <= maximo) {
            return texto;
        }
        return texto.substring(0, maximo - 1).stripTrailing() + "…";
    }
}
