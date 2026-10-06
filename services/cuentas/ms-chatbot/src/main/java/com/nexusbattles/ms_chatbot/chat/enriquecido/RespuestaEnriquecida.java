package com.nexusbattles.ms_chatbot.chat.enriquecido;

import java.util.List;

// ms-chatbot.yaml 1.3.4 (7.4.3 y 7.4.6): las partes de una respuesta del bot
// que la ventana pinta como elementos interactivos. El texto de la respuesta
// sigue siendo completo: quien no sepa pintar esto no pierde nada.
public record RespuestaEnriquecida(
    List<String> pasos,
    List<EnlaceInterno> enlaces,
    List<TarjetaInformativa> tarjetas,
    List<String> respuestasRapidas,
    boolean ofrecerSoporteHumano
) {

    public RespuestaEnriquecida {
        pasos = pasos == null ? List.of() : List.copyOf(pasos);
        enlaces = enlaces == null ? List.of() : List.copyOf(enlaces);
        tarjetas = tarjetas == null ? List.of() : List.copyOf(tarjetas);
        respuestasRapidas = respuestasRapidas == null ? List.of() : List.copyOf(respuestasRapidas);
    }

    /** true si no trae nada que pintar: no vale la pena guardarla ni enviarla. */
    public boolean estaVacia() {
        return pasos.isEmpty() && enlaces.isEmpty() && tarjetas.isEmpty() && respuestasRapidas.isEmpty()
            && !ofrecerSoporteHumano;
    }
}
