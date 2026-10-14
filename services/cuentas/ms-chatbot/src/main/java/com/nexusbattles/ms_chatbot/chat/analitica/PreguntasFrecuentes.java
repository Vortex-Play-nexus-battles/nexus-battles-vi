package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PreguntaFrecuente;
import com.nexusbattles.ms_chatbot.chat.privacidad.RedactorDeDatosSensibles;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// ms-chatbot.yaml 1.7.0 (7.4.7 «preguntas mas frecuentes y temas de
// consulta»): los temas frecuentes dicen que temas de la base respondieron
// mas; esto dice que preguntan los jugadores, con sus palabras, aunque
// ningun tema las cubra. Sin estado.
//
// Se agrupan como las lee el motor (NormalizadorTexto: sin tildes, mayusculas
// ni signos), sobre el texto ya redactado (1.4.0), y se muestra esa forma
// normalizada, recortada a LARGO_MAXIMO: nunca el texto tal cual lo escribio
// una persona.
//
// Para que el tablero no muestre lo que escribio una sola persona (7.4.8),
// una pregunta solo sale si la hicieron al menos MINIMO_DE_CONVERSACIONES
// conversaciones distintas. El documento no da la cifra: D-CHA-06, convencion
// tecnica provisional. Con 2 bastaba para una palabra suelta (palabras
// clave); una frase entera identifica mas, por eso pide una conversacion mas.
final class PreguntasFrecuentes {

    static final int MINIMO_DE_CONVERSACIONES = 3;
    static final int LARGO_MAXIMO = 200;

    private PreguntasFrecuentes() {
    }

    static List<PreguntaFrecuente> contar(List<RegistroDeTexto> preguntas, int cuantas) {
        Map<String, Integer> vecesPorTexto = new HashMap<>();
        Map<String, Set<UUID>> conversacionesPorTexto = new HashMap<>();

        for (RegistroDeTexto pregunta : preguntas) {
            String texto = clave(pregunta.contenido());
            if (texto.isEmpty()) {
                continue;
            }
            vecesPorTexto.merge(texto, 1, Integer::sum);
            conversacionesPorTexto.computeIfAbsent(texto, t -> new HashSet<>()).add(pregunta.conversacionId());
        }

        return vecesPorTexto.entrySet().stream()
            .filter(e -> conversacionesPorTexto.get(e.getKey()).size() >= MINIMO_DE_CONVERSACIONES)
            .map(e -> new PreguntaFrecuente(e.getKey(), e.getValue(), conversacionesPorTexto.get(e.getKey()).size()))
            .sorted(Comparator.comparingLong(PreguntaFrecuente::preguntas).reversed()
                .thenComparing(Comparator.comparingLong(PreguntaFrecuente::conversaciones).reversed())
                .thenComparing(PreguntaFrecuente::texto))
            .limit(cuantas)
            .toList();
    }

    // La forma normalizada y recortada; vacia si no queda pregunta (solo
    // signos, o solo lo que la redaccion tapo).
    static String clave(String contenido) {
        if (contenido == null
            || NormalizadorTexto.normalizar(RedactorDeDatosSensibles.sinMarcas(contenido)).isBlank()) {
            return "";
        }
        String normalizado = NormalizadorTexto.normalizar(contenido);
        return normalizado.length() > LARGO_MAXIMO ? normalizado.substring(0, LARGO_MAXIMO).strip() : normalizado;
    }
}
