package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.enriquecido.RespuestaEnriquecida;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
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
 * @param temaClave             clave estable del tema que respondió (HU-CHA-012, analíticas
 *                              de temas frecuentes); null si no respondió un tema de la base
 *                              de conocimiento (escalamiento o consulta asistida)
 * @param enriquecido           ms-chatbot.yaml 1.3.4: pasos, enlaces, tarjetas, respuestas
 *                              rápidas y oferta de soporte humano; null si no hay nada que
 *                              pintar
 * @param ingles                ms-chatbot.yaml 1.5.0: la respuesta está en inglés; lo que
 *                              {@code ChatService} añada (preguntas relacionadas, el aviso de
 *                              un dato tapado) va en el mismo idioma
 * @param resultado             ms-chatbot.yaml 1.7.0: cómo terminó la respuesta (tema, consulta
 *                              en vivo, servicio no disponible, escalamiento…), dicho por el motor
 *                              que la generó; se guarda con el mensaje para la tasa de resolución.
 *                              Si no se da, se deduce solo lo seguro: ESCALADO si se escaló, TEMA
 *                              si la respondió un tema; si no, queda null y esa respuesta no
 *                              cuenta en la tasa
 */
public record ResultadoMotor(
    String texto,
    Categoria categoria,
    TipoRespuesta tipoRespuesta,
    boolean requiereEscalamiento,
    List<String> temasSugeridos,
    String temaClave,
    RespuestaEnriquecida enriquecido,
    boolean ingles,
    ResultadoDeRespuesta resultado
) {

    public ResultadoMotor {
        if (resultado == null) {
            resultado = deducido(requiereEscalamiento, temaClave);
        }
    }

    /** Sin el resultado (antes de 1.7.0): se deduce lo seguro, ver {@link #resultado()}. */
    public ResultadoMotor(String texto, Categoria categoria, TipoRespuesta tipoRespuesta, boolean requiereEscalamiento,
                          List<String> temasSugeridos, String temaClave, RespuestaEnriquecida enriquecido,
                          boolean ingles) {
        this(texto, categoria, tipoRespuesta, requiereEscalamiento, temasSugeridos, temaClave, enriquecido, ingles,
            null);
    }

    public static ResultadoMotor deTema(String texto, Categoria categoria, TipoRespuesta tipoRespuesta) {
        return deTema(texto, categoria, tipoRespuesta, null);
    }

    public static ResultadoMotor deTema(String texto, Categoria categoria, TipoRespuesta tipoRespuesta,
                                        String temaClave) {
        return new ResultadoMotor(texto, categoria, tipoRespuesta, false, List.of(), temaClave, null, false);
    }

    public static ResultadoMotor escalado(String texto, List<String> temasSugeridos) {
        return new ResultadoMotor(texto, null, null, true, temasSugeridos, null, null, false);
    }

    /** Copia con la respuesta enriquecida (1.3.4). */
    public ResultadoMotor conEnriquecido(RespuestaEnriquecida nuevo) {
        return new ResultadoMotor(texto, categoria, tipoRespuesta, requiereEscalamiento, temasSugeridos, temaClave,
            nuevo, ingles, resultado);
    }

    /** Copia que dice en qué idioma quedó la respuesta (1.5.0). */
    public ResultadoMotor enIngles(boolean enIngles) {
        return new ResultadoMotor(texto, categoria, tipoRespuesta, requiereEscalamiento, temasSugeridos, temaClave,
            enriquecido, enIngles, resultado);
    }

    /** Copia con el resultado que decidió el motor (1.7.0). */
    public ResultadoMotor conResultado(ResultadoDeRespuesta nuevo) {
        return new ResultadoMotor(texto, categoria, tipoRespuesta, requiereEscalamiento, temasSugeridos, temaClave,
            enriquecido, ingles, nuevo);
    }

    private static ResultadoDeRespuesta deducido(boolean requiereEscalamiento, String temaClave) {
        if (requiereEscalamiento) {
            return ResultadoDeRespuesta.ESCALADO;
        }
        return temaClave != null ? ResultadoDeRespuesta.TEMA : null;
    }
}
