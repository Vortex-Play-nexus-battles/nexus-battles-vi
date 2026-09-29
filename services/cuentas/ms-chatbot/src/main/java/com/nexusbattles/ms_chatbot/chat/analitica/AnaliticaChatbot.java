package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;

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
    List<PuntoDeTendencia> tendencia,
    ResumenDeTickets tickets,
    List<PalabraClave> palabrasClave
) {

    // ms-chatbot.yaml 1.3.7: tickets y palabras clave son adicionales; este
    // constructor deja los de antes de 1.3.7 sin tocar.
    public AnaliticaChatbot(Instant desde, Instant hasta, String zonaHoraria, long conversaciones, long preguntas,
                            long respuestasMedidas, long escalamientos, Double tasaResolucion,
                            Double tiempoRespuestaPromedioMs, long calificaciones, long calificacionesUtiles,
                            Double satisfaccion, List<TemaFrecuente> temasFrecuentes,
                            List<PuntoDeTendencia> tendencia) {
        this(desde, hasta, zonaHoraria, conversaciones, preguntas, respuestasMedidas, escalamientos, tasaResolucion,
            tiempoRespuestaPromedioMs, calificaciones, calificacionesUtiles, satisfaccion, temasFrecuentes, tendencia,
            ResumenDeTickets.VACIO, List.of());
    }

    public record TemaFrecuente(String clave, String titulo, long respuestas) {
    }

    public record PuntoDeTendencia(LocalDate dia, long conversaciones, long preguntas,
                                   long respuestas, long escalamientos) {
    }

    /** 1.3.7: los tickets de soporte abiertos en el periodo. */
    public record ResumenDeTickets(long total, long abiertos, long enProceso, long resueltos, long cerrados,
                                   Double horasPromedioDeAtencion, List<TicketsPorCategoria> porCategoria) {

        public static final ResumenDeTickets VACIO = new ResumenDeTickets(0, 0, 0, 0, 0, null, List.of());
    }

    /** 1.3.7: cuantos tickets hubo de una categoria. */
    public record TicketsPorCategoria(Categoria categoria, long tickets) {
    }

    /** 1.3.7: una palabra de las preguntas, en cuantas preguntas y conversaciones aparece. */
    public record PalabraClave(String palabra, long preguntas, long conversaciones) {
    }
}
