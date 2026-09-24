package com.nexusbattles.ms_chatbot.chat.conocimiento;

import java.util.Arrays;
import java.util.List;

// HU-CHA-012 (RF-CHA-013): las "variantes de pregunta" de un tema son sus
// frases de palabras clave. En la base se guardan como un solo texto separado
// por comas (asi las lee MotorRespuestas desde HU-CHA-004); en la API viajan
// como lista. Esta clase traduce entre las dos formas.
public final class VariantesDePregunta {

    public static final String SEPARADOR = ",";

    private VariantesDePregunta() {
    }

    public static String unir(List<String> variantes) {
        if (variantes == null || variantes.isEmpty()) {
            return null;
        }
        List<String> limpias = variantes.stream()
            .filter(v -> v != null && !v.isBlank())
            .map(String::trim)
            .toList();
        return limpias.isEmpty() ? null : String.join(SEPARADOR + " ", limpias);
    }

    public static List<String> separar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return Arrays.stream(texto.split(SEPARADOR))
            .map(String::trim)
            .filter(v -> !v.isEmpty())
            .toList();
    }

    // El motor parte las palabras clave por coma: una variante con coma se
    // convertiria en dos variantes distintas sin que el administrador lo note.
    public static boolean algunaContieneSeparador(List<String> variantes) {
        return variantes != null && variantes.stream().anyMatch(v -> v != null && v.contains(SEPARADOR));
    }
}
