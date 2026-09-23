package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Motor de respuestas del chatbot (HU-CHA-004): un motor basado en reglas e
 * intenciones (no en un modelo de lenguaje) que reconoce la intención de un
 * mensaje comparándolo contra las palabras clave de la base de conocimiento,
 * tolerando errores ortográficos mediante distancia de edición (Levenshtein).
 */
@Service
public class MotorRespuestas {

    /** Puntaje mínimo para considerar que un tema resuelve la consulta sin escalar. */
    private static final int UMBRAL_CONFIANZA = 3;

    private static final int PUNTAJE_FRASE_EXACTA = 3;
    private static final int PUNTAJE_PALABRA_CORTA = 1;
    private static final int PUNTAJE_PALABRA_LARGA = 2;

    private static final int DISTANCIA_MAXIMA_PALABRA_LARGA = 1;
    private static final int DISTANCIA_MAXIMA_PALABRA_CORTA = 1;
    private static final int LONGITUD_PALABRA_LARGA = 5;

    /**
     * Palabras gramaticales (stopwords) en español e inglés que no aportan
     * intención por sí solas y por lo tanto no deben sumar puntaje al
     * compararlas palabra por palabra (sí pueden formar parte de una frase
     * completa evaluada como coincidencia exacta).
     */
    private static final Set<String> PALABRAS_VACIAS = Set.of(
        "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "a",
        "ante", "con", "contra", "desde", "en", "entre", "hacia", "hasta", "para",
        "por", "segun", "sin", "sobre", "tras", "que", "como", "cuando", "donde",
        "quien", "quienes", "cual", "cuales", "es", "son", "ser", "esta", "estan",
        "esto", "ese", "esa", "eso", "esos", "esas", "mi", "tu", "su", "me", "te",
        "se", "nos", "os", "les", "lo", "no", "si", "ya", "mas", "pero", "del", "al",
        "yo", "ella", "ellos", "ellas", "nosotros", "ustedes",
        "the", "an", "is", "are", "was", "were", "be", "been", "of", "to", "in",
        "on", "for", "and", "or", "this", "that", "these", "those", "i", "you", "he",
        "she", "it", "we", "they", "do", "does", "did", "how", "what", "when",
        "where", "who", "which", "my", "your", "his", "her", "its", "our", "their",
        "with", "at", "by", "from", "as", "but", "if", "so", "not", "can"
    );

    private final TemaConocimientoRepository temaConocimientoRepository;

    public MotorRespuestas(TemaConocimientoRepository temaConocimientoRepository) {
        this.temaConocimientoRepository = temaConocimientoRepository;
    }

    public ResultadoMotor generarRespuesta(String mensajeUsuario) {
        String mensajeNormalizado = NormalizadorTexto.normalizar(mensajeUsuario);
        List<String> palabrasMensaje = List.of(mensajeNormalizado.split(" "));

        List<TemaConocimiento> temasActivos = temaConocimientoRepository.findByActivoTrue();

        Coincidencia mejor = null;
        List<Coincidencia> todas = new ArrayList<>();

        for (TemaConocimiento tema : temasActivos) {
            int puntajeEs = puntuar(palabrasMensaje, mensajeNormalizado, tema.getPalabrasClaveEs());
            int puntajeEn = puntuar(palabrasMensaje, mensajeNormalizado, tema.getPalabrasClaveEn());

            boolean esIngles = puntajeEn > puntajeEs;
            int puntaje = esIngles ? puntajeEn : puntajeEs;

            if (puntaje > 0) {
                Coincidencia coincidencia = new Coincidencia(tema, puntaje, esIngles);
                todas.add(coincidencia);
                if (mejor == null || puntaje > mejor.puntaje()) {
                    mejor = coincidencia;
                }
            }
        }

        if (mejor != null && mejor.puntaje() >= UMBRAL_CONFIANZA) {
            TemaConocimiento tema = mejor.tema();
            String texto = mejor.esIngles() ? tema.getContenidoRespuestaEn() : tema.getContenidoRespuestaEs();
            if (texto == null || texto.isBlank()) {
                texto = tema.getContenidoRespuestaEs();
            }
            return ResultadoMotor.deTema(texto, tema.getCategoria(), tema.getTipoRespuesta());
        }

        List<String> sugerencias = todas.stream()
            .sorted(Comparator.comparingInt(Coincidencia::puntaje).reversed())
            .map(c -> c.tema().getTitulo())
            .distinct()
            .limit(3)
            .toList();

        String textoEscalamiento = "No estoy seguro de haber entendido tu consulta. "
            + "Puedo ponerte en contacto con soporte humano para resolverla; "
            + "mientras tanto, esta pregunta quedó registrada para mejorar mis respuestas.";

        return ResultadoMotor.escalado(textoEscalamiento, sugerencias);
    }

    private int puntuar(List<String> palabrasMensaje, String mensajeNormalizado, String palabrasClaveCrudas) {
        if (palabrasClaveCrudas == null || palabrasClaveCrudas.isBlank()) {
            return 0;
        }

        int puntaje = 0;
        Set<String> palabrasClaveUnicas = new LinkedHashSet<>();

        for (String fraseClave : palabrasClaveCrudas.split(",")) {
            String fraseNormalizada = NormalizadorTexto.normalizar(fraseClave);
            if (fraseNormalizada.isBlank()) {
                continue;
            }

            if (mensajeNormalizado.contains(fraseNormalizada)) {
                puntaje += PUNTAJE_FRASE_EXACTA;
            }

            for (String palabra : fraseNormalizada.split(" ")) {
                if (!palabra.isBlank() && !PALABRAS_VACIAS.contains(palabra)) {
                    palabrasClaveUnicas.add(palabra);
                }
            }
        }

        for (String palabraClave : palabrasClaveUnicas) {
            for (String palabraMensaje : palabrasMensaje) {
                if (coincidenAproximadamente(palabraClave, palabraMensaje)) {
                    puntaje += esPalabraLarga(palabraClave) ? PUNTAJE_PALABRA_LARGA : PUNTAJE_PALABRA_CORTA;
                    break;
                }
            }
        }

        return puntaje;
    }

    private boolean coincidenAproximadamente(String palabraClave, String palabraMensaje) {
        if (palabraClave.isBlank() || palabraMensaje.isBlank()) {
            return false;
        }
        if (palabraClave.equals(palabraMensaje)) {
            return true;
        }
        int distanciaMaxima = esPalabraLarga(palabraClave)
            ? DISTANCIA_MAXIMA_PALABRA_LARGA
            : DISTANCIA_MAXIMA_PALABRA_CORTA;
        return distanciaLevenshtein(palabraClave, palabraMensaje) <= distanciaMaxima;
    }

    private boolean esPalabraLarga(String palabra) {
        return palabra.length() >= LONGITUD_PALABRA_LARGA;
    }

    /** Distancia de edición de Levenshtein clásica (programación dinámica). */
    private int distanciaLevenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + costo
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    /** Coincidencia interna de un tema con su puntaje e idioma detectado. */
    private record Coincidencia(TemaConocimiento tema, int puntaje, boolean esIngles) {
    }
}
