package com.nexusbattles.ms_chatbot.chat.analitica;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// HU-CHA-012 (RF-CHA-012): tablero de analiticas del chatbot para un periodo.
//
// Tasas como proporcion entre 0 y 1; null cuando no hay datos para
// calcularlas (por ejemplo, satisfaccion sin ninguna calificacion), para no
// confundir "sin datos" con "0 %".
public record AnaliticaChatbot(
    Instant desde,
    Instant hasta,
    String zonaHoraria,
    long conversaciones,
    long preguntas,
    long respuestasMedidas,
    long escalamientos,
    Double tasaResolucion,
    Double tiempoRespuestaPromedioMs,
    long calificaciones,
    long calificacionesUtiles,
    Double satisfaccion,
    List<TemaFrecuente> temasFrecuentes,
    List<PuntoDeTendencia> tendencia
) {

    public record TemaFrecuente(String clave, String titulo, long respuestas) {
    }

    public record PuntoDeTendencia(LocalDate dia, long conversaciones, long preguntas,
                                   long respuestas, long escalamientos) {
    }
}
