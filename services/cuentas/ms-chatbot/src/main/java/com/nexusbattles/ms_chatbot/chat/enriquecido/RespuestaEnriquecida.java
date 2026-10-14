package com.nexusbattles.ms_chatbot.chat.enriquecido;

import java.util.List;

// ms-chatbot.yaml 1.3.4 (7.4.3 y 7.4.6): las partes de una respuesta del bot
// que la ventana pinta como elementos interactivos. El texto de la respuesta
// sigue siendo completo: quien no sepa pintar esto no pierde nada.
//
// 1.12.0 (7.4.3, RF-CHA-007): `solicitud` es la solicitud de soporte que el
// asistente abrio (o a la que sumo la pregunta) porque no pudo responder;
// null en cualquier otra respuesta y en las guardadas antes.
public record RespuestaEnriquecida(
    List<String> pasos,
    List<EnlaceInterno> enlaces,
    List<TarjetaInformativa> tarjetas,
    List<String> respuestasRapidas,
    boolean ofrecerSoporteHumano,
    SolicitudAbierta solicitud
) {

    public RespuestaEnriquecida {
        pasos = pasos == null ? List.of() : List.copyOf(pasos);
        enlaces = enlaces == null ? List.of() : List.copyOf(enlaces);
        tarjetas = tarjetas == null ? List.of() : List.copyOf(tarjetas);
        respuestasRapidas = respuestasRapidas == null ? List.of() : List.copyOf(respuestasRapidas);
    }

    /** Sin solicitud: todas las respuestas menos la de una consulta que no se pudo resolver. */
    public RespuestaEnriquecida(List<String> pasos, List<EnlaceInterno> enlaces, List<TarjetaInformativa> tarjetas,
                                List<String> respuestasRapidas, boolean ofrecerSoporteHumano) {
        this(pasos, enlaces, tarjetas, respuestasRapidas, ofrecerSoporteHumano, null);
    }

    /** La misma respuesta con la solicitud que se abrio por ella. */
    public RespuestaEnriquecida conSolicitud(SolicitudAbierta nueva) {
        return new RespuestaEnriquecida(pasos, enlaces, tarjetas, respuestasRapidas, ofrecerSoporteHumano, nueva);
    }

    /** true si no trae nada que pintar: no vale la pena guardarla ni enviarla. */
    public boolean estaVacia() {
        return pasos.isEmpty() && enlaces.isEmpty() && tarjetas.isEmpty() && respuestasRapidas.isEmpty()
            && !ofrecerSoporteHumano && solicitud == null;
    }
}
