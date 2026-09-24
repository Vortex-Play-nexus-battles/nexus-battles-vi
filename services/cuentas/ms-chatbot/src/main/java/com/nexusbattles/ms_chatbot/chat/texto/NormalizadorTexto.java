package com.nexusbattles.ms_chatbot.chat.texto;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

// Normalizacion de texto compartida por todo el chatbot: MotorRespuestas
// (HU-CHA-004), MotorConsultasAsistidas (HU-CHA-008) y la agrupacion de
// brechas de conocimiento (HU-CHA-011). Antes estaba copiada como metodo
// privado en cada motor; se centraliza aqui para que las tres partes
// normalicen EXACTAMENTE igual. Esto importa sobre todo para las brechas:
// dos preguntas que solo difieren en tildes, mayusculas o signos deben
// agruparse en la misma fila.
//
// Reglas: minusculas, sin tildes/acentos, sin signos de puntuacion (se
// reemplazan por espacio) y espacios multiples colapsados en uno.
public final class NormalizadorTexto {

    private static final Locale ESPANOL = Locale.forLanguageTag("es");
    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}");
    private static final Pattern CARACTERES_NO_ALFANUMERICOS = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern ESPACIOS_MULTIPLES = Pattern.compile("\\s+");

    private NormalizadorTexto() {
        // Clase de utilidades: no se instancia.
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinAcentos = MARCAS_DIACRITICAS
            .matcher(Normalizer.normalize(texto.toLowerCase(ESPANOL), Normalizer.Form.NFD))
            .replaceAll("");
        String soloAlfanumerico = CARACTERES_NO_ALFANUMERICOS.matcher(sinAcentos).replaceAll(" ");
        return ESPACIOS_MULTIPLES.matcher(soloAlfanumerico).replaceAll(" ").trim();
    }
}
