package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // ms-chatbot.yaml 1.6.0: como unir, pero la misma variante escrita dos
    // veces (sin contar tildes, mayusculas ni signos: como la compara el
    // motor) queda una sola vez, con la forma de su primera aparicion. No es
    // un error: sumaba puntaje dos veces por la misma frase y nada mas. Es lo
    // que usa el panel; la migracion V11 sigue con unir, tal como se aplico.
    public static String unirSinRepetir(List<String> variantes) {
        if (variantes == null) {
            return null;
        }
        Set<String> vistas = new HashSet<>();
        List<String> unicas = new ArrayList<>();
        for (String variante : variantes) {
            if (variante != null && !variante.isBlank() && vistas.add(NormalizadorTexto.normalizar(variante))) {
                unicas.add(variante);
            }
        }
        return unir(unicas);
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
