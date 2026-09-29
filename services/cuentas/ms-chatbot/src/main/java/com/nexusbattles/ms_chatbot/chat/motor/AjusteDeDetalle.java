package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.NivelDeDetalle;

import java.util.List;
import java.util.regex.Pattern;

// ms-chatbot.yaml 1.3.6 (7.4.5 «nivel de detalle»): adapta el texto de un tema
// al nivel que prefiere quien pregunta. Sin estado.
//
//   * BREVE: solo la primera oracion. Un tema paso a paso queda entero: sin
//     sus pasos no sirve.
//   * NORMAL: el texto tal cual.
//   * DETALLADO: el texto y, al final, los temas relacionados (los mismos que
//     salen como botones de respuesta rapida).
public final class AjusteDeDetalle {

    // Fin de oracion: . ! o ? seguido de espacio y de una mayuscula (o de ¡ ¿).
    // Asi "1,2^(N-1) puntos" o "el 50 %" no cortan la oracion.
    private static final Pattern FIN_DE_ORACION = Pattern.compile("(?<=[.!?])\\s+(?=[\\p{Lu}¡¿])");

    static final String RELACIONADOS_ES = "Temas relacionados: ";
    static final String RELACIONADOS_EN = "Related topics: ";

    private AjusteDeDetalle() {
        // Utilidades sin estado.
    }

    public static String aplicar(String texto, TipoRespuesta tipo, NivelDeDetalle nivel, List<String> relacionados,
                                 boolean ingles) {
        if (texto == null || texto.isBlank() || nivel == null) {
            return texto;
        }
        return switch (nivel) {
            case NORMAL -> texto;
            case BREVE -> tipo == TipoRespuesta.PASO_A_PASO ? texto : primeraOracion(texto);
            case DETALLADO -> conRelacionados(texto, relacionados, ingles);
        };
    }

    static String primeraOracion(String texto) {
        String[] oraciones = FIN_DE_ORACION.split(texto.strip(), 2);
        return oraciones[0].strip();
    }

    private static String conRelacionados(String texto, List<String> relacionados, boolean ingles) {
        if (relacionados == null || relacionados.isEmpty()) {
            return texto;
        }
        return texto.strip() + " " + (ingles ? RELACIONADOS_EN : RELACIONADOS_ES)
            + String.join(" | ", relacionados) + ".";
    }
}
