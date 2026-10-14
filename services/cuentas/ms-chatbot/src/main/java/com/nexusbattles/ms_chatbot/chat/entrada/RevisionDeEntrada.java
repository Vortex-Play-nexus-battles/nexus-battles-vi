package com.nexusbattles.ms_chatbot.chat.entrada;

import com.nexusbattles.ms_chatbot.chat.privacidad.RedaccionDeDatosSensibles;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 7.4.8 — lo primero que le pasa a cualquier texto que escribe el usuario
 * (pregunta, comentario de una calificación, asunto y mensaje de un ticket),
 * antes de que el chatbot lo procese, lo modere, lo guarde o lo mande a otro
 * servicio:
 *
 * <ol>
 *   <li>Se quitan los caracteres de control (salvo tabulador y saltos de
 *       línea) y los invisibles que sirven para esconder texto: los de ancho
 *       cero y los que invierten la dirección de escritura. Un NUL, además,
 *       hacía fallar a PostgreSQL con un 500.</li>
 *   <li>Si trae un intento de inyección de código ({@link DetectorDeInyeccion})
 *       se rechaza con {@link InyeccionDetectada}: nada se guarda. Se cuenta
 *       en la métrica {@code chatbot.inyecciones.bloqueadas} por tipo y se
 *       anota en la bitácora el tipo, nunca el texto.</li>
 *   <li>Se tapan las contraseñas, tarjetas, tokens y claves
 *       ({@link RedaccionDeDatosSensibles}). Desde aquí en adelante solo
 *       circula el texto redactado: la lista negra, los motores, las brechas
 *       de conocimiento, las analíticas y la base nunca ven el original.</li>
 * </ol>
 */
@Component
public class RevisionDeEntrada {

    private static final Logger log = LoggerFactory.getLogger(RevisionDeEntrada.class);

    // Controles C0 y C1 salvo \t \n \r; espacio de ancho cero, marcas de
    // direccion, incrustaciones e invertidos bidireccionales, unidor de
    // palabras e invisibles, y la marca de orden de bytes.
    private static final Pattern INVISIBLES = Pattern.compile(
        "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F\\u200B\\u200E\\u200F\\u202A-\\u202E\\u2060-\\u2064"
            + "\\u2066-\\u2069\\uFEFF]");

    /** El texto listo para usarse y si la redacción tapó algo. */
    public record Revisada(String texto, boolean seTapoAlgo) {
    }

    private final RedaccionDeDatosSensibles redaccion;
    private final MeterRegistry metricas;

    public RevisionDeEntrada(RedaccionDeDatosSensibles redaccion, MeterRegistry metricas) {
        this.redaccion = redaccion;
        this.metricas = metricas;
    }

    /**
     * Limpia, detecta inyección y redacta. {@code null} devuelve {@code null};
     * un texto que tras limpiar queda vacío devuelve la cadena vacía (quien
     * llama decide si eso es un 400 o «sin comentario»).
     */
    public Revisada revisar(String texto) {
        if (texto == null) {
            return new Revisada(null, false);
        }
        String limpio = INVISIBLES.matcher(texto).replaceAll("").strip();
        DetectorDeInyeccion.detectar(limpio).ifPresent(tipo -> {
            Counter.builder("chatbot.inyecciones.bloqueadas")
                .description("Intentos de inyeccion de codigo bloqueados (7.4.8)")
                .tag("tipo", tipo.name())
                .register(metricas)
                .increment();
            log.info("Intento de inyeccion de codigo bloqueado: tipo={}", tipo);
            throw new InyeccionDetectada(tipo);
        });
        String redactado = redaccion.redactar(limpio);
        return new Revisada(redactado, !redactado.equals(limpio));
    }

    /** Atajo para quien solo necesita el texto ya revisado. */
    public String textoRevisado(String texto) {
        return revisar(texto).texto();
    }
}
