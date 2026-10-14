package com.nexusbattles.ms_chatbot.chat.enriquecido;

import java.util.UUID;

/**
 * ms-chatbot.yaml 1.12.0 (7.4.3, RF-CHA-007 «generar automáticamente un ticket
 * de soporte»): la solicitud que el asistente abrió —o a la que sumó la
 * pregunta— cuando no pudo responder. La ventana la enseña en la respuesta,
 * con un botón a «Tus solicitudes».
 *
 * @param id     la solicitud
 * @param asunto su asunto, ya redactado
 * @param estado ABIERTO, EN_PROCESO… como en «Tus solicitudes»
 * @param nueva  si se abrió con esta pregunta (false: ya había una abierta y
 *               la pregunta se sumó a ella)
 */
public record SolicitudAbierta(UUID id, String asunto, String estado, boolean nueva) {
}
