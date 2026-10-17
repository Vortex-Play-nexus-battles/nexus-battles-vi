package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;

import java.util.List;
import java.util.Optional;

/**
 * ms-chatbot.yaml 1.8.0 (7.4.3, «escalamiento a agente humano con contexto de
 * la conversación»): la pregunta que se lleva a soporte cuando el jugador
 * pulsa «Crear solicitud con esta conversación».
 *
 * <p>Se busca desde el final de la conversación:
 *
 * <ol>
 *   <li>La última respuesta del bot que no resolvió nada y ofreció soporte:
 *       {@code ESCALADO} (no entendió) o {@code NO_SIRVIO}. Las anteriores a
 *       V13 no tienen resultado: cuenta la marca {@code escalado}, y si la
 *       pregunta de antes es un «no me sirvió», es un {@code NO_SIRVIO}.
 *       <ul>
 *         <li>Si no entendió, la pregunta es la del jugador justo antes.</li>
 *         <li>Si «no le sirvió», la pregunta no es el «no me sirvió»: es la
 *             que respondió la respuesta anterior a ese «no me sirvió».</li>
 *       </ul>
 *   </li>
 *   <li>Si nada quedó sin resolver, la última pregunta del jugador (no un «más
 *       detalle» ni un «en inglés», que no preguntan nada).</li>
 * </ol>
 *
 * <p>La categoría es la que guardó la respuesta: la del tema que no sirvió
 * (o el que respondió la última pregunta); {@code null} si no hubo tema, y
 * entonces decide quien llama.
 *
 * <p>Solo lee la lista que recibe: de qué conversación sale la decide quien
 * llama, con la identidad verificada.
 *
 * @param texto     la pregunta, tal como se guardó (ya redactada)
 * @param categoria la del tema de la respuesta; {@code null} si no hubo
 * @param motivo    por qué se lleva esa
 */
public record PreguntaParaSoporte(String texto, Categoria categoria, Motivo motivo) {

    /** Por qué se eligió esa pregunta. */
    public enum Motivo {
        /** El asistente no la entendió y ofreció soporte. */
        NO_LA_ENTENDIO,
        /** La respuesta no le sirvió al jugador. */
        NO_LE_SIRVIO,
        /** No quedó nada sin resolver: es la última pregunta. */
        ULTIMA_PREGUNTA
    }

    /**
     * @param conversacion los mensajes de una conversación, del más antiguo al
     *                     más reciente
     * @return la pregunta, o vacío si el jugador no preguntó nada
     */
    public static Optional<PreguntaParaSoporte> de(List<Mensaje> conversacion) {
        if (conversacion == null || conversacion.isEmpty()) {
            return Optional.empty();
        }
        for (int i = conversacion.size() - 1; i >= 0; i--) {
            Optional<PreguntaParaSoporte> sinResolver = sinResolverEn(conversacion, i);
            if (sinResolver.isPresent()) {
                return sinResolver;
            }
        }
        for (int i = conversacion.size() - 1; i >= 0; i--) {
            Mensaje mensaje = conversacion.get(i);
            if (esPregunta(mensaje)) {
                Categoria categoria = i + 1 < conversacion.size() && esDelBot(conversacion.get(i + 1))
                    ? conversacion.get(i + 1).getCategoria()
                    : null;
                return Optional.of(new PreguntaParaSoporte(mensaje.getContenido(), categoria,
                    Motivo.ULTIMA_PREGUNTA));
            }
        }
        return Optional.empty();
    }

    // La pregunta sin resolver que deja la respuesta del bot en `i`, si esa
    // respuesta no resolvio nada.
    private static Optional<PreguntaParaSoporte> sinResolverEn(List<Mensaje> conversacion, int i) {
        Mensaje respuesta = conversacion.get(i);
        if (!esDelBot(respuesta) || !ofrecioSoporte(respuesta)) {
            return Optional.empty();
        }
        int anterior = anteriorDe(conversacion, i, Remitente.USUARIO);
        if (anterior < 0) {
            return Optional.empty();
        }
        Mensaje dicho = conversacion.get(anterior);
        // Desde V13 lo dice el resultado; antes, que lo dicho fuera un «no me
        // sirvio».
        boolean noLeSirvio = respuesta.getResultado() != null
            ? respuesta.getResultado() == ResultadoDeRespuesta.NO_SIRVIO
            : Seguimiento.de(dicho.getContenido()).filter(Seguimiento.NO_SIRVIO::equals).isPresent();
        if (!noLeSirvio) {
            return esPregunta(dicho)
                ? Optional.of(new PreguntaParaSoporte(dicho.getContenido(), respuesta.getCategoria(),
                    Motivo.NO_LA_ENTENDIO))
                : Optional.empty();
        }
        // «No me sirvio»: la pregunta es la que respondio la respuesta de antes.
        int queNoSirvio = anteriorDe(conversacion, anterior, Remitente.BOT);
        int pregunta = queNoSirvio < 0 ? -1 : anteriorDe(conversacion, queNoSirvio, Remitente.USUARIO);
        if (pregunta < 0 || !esPregunta(conversacion.get(pregunta))) {
            return Optional.empty();
        }
        Categoria categoria = respuesta.getCategoria() != null
            ? respuesta.getCategoria()
            : conversacion.get(queNoSirvio).getCategoria();
        return Optional.of(new PreguntaParaSoporte(conversacion.get(pregunta).getContenido(), categoria,
            Motivo.NO_LE_SIRVIO));
    }

    private static boolean ofrecioSoporte(Mensaje respuesta) {
        ResultadoDeRespuesta resultado = respuesta.getResultado();
        if (resultado != null) {
            return resultado == ResultadoDeRespuesta.ESCALADO || resultado == ResultadoDeRespuesta.NO_SIRVIO;
        }
        return Boolean.TRUE.equals(respuesta.getEscalado());
    }

    private static boolean esDelBot(Mensaje mensaje) {
        return mensaje.getRemitente() == Remitente.BOT;
    }

    // Una pregunta del jugador: no un seguimiento («mas detalle», «no me
    // sirvio»), que no pregunta nada nuevo.
    private static boolean esPregunta(Mensaje mensaje) {
        return mensaje.getRemitente() == Remitente.USUARIO
            && mensaje.getContenido() != null
            && !mensaje.getContenido().isBlank()
            && Seguimiento.de(mensaje.getContenido()).isEmpty();
    }

    private static int anteriorDe(List<Mensaje> conversacion, int desde, Remitente remitente) {
        for (int i = desde - 1; i >= 0; i--) {
            if (conversacion.get(i).getRemitente() == remitente) {
                return i;
            }
        }
        return -1;
    }
}
