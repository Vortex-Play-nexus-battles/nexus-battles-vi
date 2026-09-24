package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;

import java.util.List;

/**
 * Resultado que produce el {@code MotorRespuestas} para un mensaje del usuario.
 *
 * @param texto                el texto de la respuesta que se le mostrará al usuario
 * @param categoria             categoría del tema que respondió (null si no hubo coincidencia)
 * @param tipoRespuesta         tipo de respuesta construida (null si no hubo coincidencia)
 * @param requiereEscalamiento  true si ningún tema superó el umbral de confianza, y por lo
 *                              tanto se debe ofrecer contacto con soporte humano
 * @param temasSugeridos        hasta 3 títulos de temas relacionados, para sugerir cuando
 *                              la consulta se escala o hubo ambigüedad
 */
public record ResultadoMotor(
    String texto,
    Categoria categoria,
    TipoRespuesta tipoRespuesta,
    boolean requiereEscalamiento,
    List<String> temasSugeridos
) {

    public static ResultadoMotor deTema(String texto, Categoria categoria, TipoRespuesta tipoRespuesta) {
        return new ResultadoMotor(texto, categoria, tipoRespuesta, false, List.of());
    }

    public static ResultadoMotor escalado(String texto, List<String> temasSugeridos) {
        return new ResultadoMotor(texto, null, null, true, temasSugeridos);
    }
}
