package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PalabraClave;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// ms-chatbot.yaml 1.3.7 (7.4.7 «palabras clave»): las palabras que mas usan
// los jugadores en sus preguntas. Sin estado.
//
// Se cuentan como el motor lee: sin tildes, mayusculas ni signos. Una palabra
// cuenta una vez por pregunta aunque se repita en ella.
//
// Para que el tablero no muestre datos de una persona (7.4.8), una palabra
// solo aparece si la usaron al menos MINIMO_DE_CONVERSACIONES conversaciones
// distintas, y nunca se cuentan palabras con digitos (telefonos, codigos,
// numeros de documento) ni de menos de LARGO_MINIMO letras.
final class PalabrasClaveFrecuentes {

    static final int LARGO_MINIMO = 4;
    static final int MINIMO_DE_CONVERSACIONES = 2;

    // Palabras que no dicen de que trata una pregunta. Mas amplia que la del
    // motor: aqui tampoco aportan "quiero", "puedo", "hola" o "gracias".
    static final Set<String> PALABRAS_VACIAS = Set.of(
        "para", "pero", "como", "cuando", "donde", "quien", "cual", "cuales", "porque", "esta", "estan",
        "este", "esto", "estos", "esas", "esos", "desde", "hasta", "sobre", "entre",
        "tengo", "tiene", "tienen", "tener", "hace", "hacer", "hago", "puedo", "puede", "pueden", "poder",
        "quiero", "quieres", "quiere", "necesito", "saber", "sabes", "algo", "alguien", "todo", "todos",
        "mucho", "muchas", "muchos", "otra", "otro", "otras", "otros", "mismo", "misma", "solo", "tambien",
        "ahora", "aqui", "alli", "entonces", "luego", "bien", "hola", "buenas", "buenos", "dias", "tardes",
        "noches", "gracias", "favor", "porfa", "ayuda", "ayudame", "ayudar", "dime", "decir", "sera", "seria",
        "cuanto", "cuanta", "cuantos", "cuantas", "that", "this", "with", "what", "when", "where",
        "which", "have", "does", "from", "your", "about", "there", "their", "would", "could", "should", "want",
        "need", "please", "thanks", "hello");

    private PalabrasClaveFrecuentes() {
    }

    static List<PalabraClave> contar(List<RegistroDeTexto> preguntas, int cuantas) {
        Map<String, Integer> preguntasPorPalabra = new HashMap<>();
        Map<String, Set<UUID>> conversacionesPorPalabra = new HashMap<>();

        for (RegistroDeTexto pregunta : preguntas) {
            for (String palabra : palabrasDe(pregunta.contenido())) {
                preguntasPorPalabra.merge(palabra, 1, Integer::sum);
                conversacionesPorPalabra.computeIfAbsent(palabra, p -> new HashSet<>())
                    .add(pregunta.conversacionId());
            }
        }

        return preguntasPorPalabra.entrySet().stream()
            .filter(e -> conversacionesPorPalabra.get(e.getKey()).size() >= MINIMO_DE_CONVERSACIONES)
            .map(e -> new PalabraClave(e.getKey(), e.getValue(), conversacionesPorPalabra.get(e.getKey()).size()))
            .sorted(Comparator.comparingLong(PalabraClave::preguntas).reversed()
                .thenComparing(PalabraClave::palabra))
            .limit(cuantas)
            .toList();
    }

    // Las palabras distintas de una pregunta que dicen algo de su tema.
    static Set<String> palabrasDe(String texto) {
        Set<String> palabras = new LinkedHashSet<>();
        String normalizado = NormalizadorTexto.normalizar(texto);
        if (normalizado.isEmpty()) {
            return palabras;
        }
        for (String palabra : normalizado.split(" ")) {
            if (palabra.length() >= LARGO_MINIMO && !contieneDigito(palabra) && !PALABRAS_VACIAS.contains(palabra)) {
                palabras.add(palabra);
            }
        }
        return palabras;
    }

    private static boolean contieneDigito(String palabra) {
        return palabra.chars().anyMatch(Character::isDigit);
    }
}
