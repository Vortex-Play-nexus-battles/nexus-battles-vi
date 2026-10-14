package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// HU-CHA-012 (RF-CHA-012): tablero de analiticas del chatbot para un periodo.
//
// Tasas como proporcion entre 0 y 1; null cuando no hay datos para
// calcularlas (por ejemplo, satisfaccion sin ninguna calificacion), para no
// confundir "sin datos" con "0 %".
//
// ms-chatbot.yaml 1.7.0 (7.4.7): la formula de cada cifra esta en el
// contrato (AnaliticaChatbot) y en AnaliticaChatbotService. En corto:
//   conversaciones               iniciadas en el periodo (fecha_inicio)
//   conversacionesConActividad   con alguna pregunta en el periodo (lo de antes)
//   escalamientos                veces que el ASISTENTE ofrecio soporte humano
//   respuestasClasificadas       respuestas con resultado guardado (desde V13)
//   respuestasResueltas          de esas, TEMA, CONSULTA_EN_VIVO o NAVEGACION
//   tasaResolucion               resueltas / clasificadas
public record AnaliticaChatbot(
    Instant desde,
    Instant hasta,
    String zonaHoraria,
    long conversaciones,
    long conversacionesConActividad,
    long preguntas,
    long respuestasMedidas,
    long escalamientos,
    long respuestasClasificadas,
    long respuestasResueltas,
    Double tasaResolucion,
    List<RespuestasPorResultado> resultados,
    Double tiempoRespuestaPromedioMs,
    long calificaciones,
    long calificacionesUtiles,
    Double satisfaccion,
    List<TemaFrecuente> temasFrecuentes,
    List<PreguntaFrecuente> preguntasFrecuentes,
    List<PuntoDeTendencia> tendencia,
    ResumenDeTickets tickets,
    List<PalabraClave> palabrasClave
) {

    // Las cifras de antes de 1.3.7: lo demas, vacio. Lo usan las pruebas que
    // solo miran esas cifras.
    public AnaliticaChatbot(Instant desde, Instant hasta, String zonaHoraria, long conversaciones, long preguntas,
                            long respuestasMedidas, long escalamientos, Double tasaResolucion,
                            Double tiempoRespuestaPromedioMs, long calificaciones, long calificacionesUtiles,
                            Double satisfaccion, List<TemaFrecuente> temasFrecuentes,
                            List<PuntoDeTendencia> tendencia) {
        this(desde, hasta, zonaHoraria, conversaciones, 0, preguntas, respuestasMedidas, escalamientos, 0, 0,
            tasaResolucion, List.of(), tiempoRespuestaPromedioMs, calificaciones, calificacionesUtiles, satisfaccion,
            temasFrecuentes, List.of(), tendencia, ResumenDeTickets.VACIO, List.of());
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

    /**
     * 1.3.7: una palabra de las preguntas, en cuantas preguntas y conversaciones aparece.
     * 1.7.0: y en cuantas preguntas del periodo anterior de la misma duracion, con su tendencia.
     */
    public record PalabraClave(String palabra, long preguntas, long conversaciones, long preguntasPeriodoAnterior,
                               TendenciaDePalabra tendencia) {

        /** Sin periodo anterior que comparar: cuenta como nueva. */
        public PalabraClave(String palabra, long preguntas, long conversaciones) {
            this(palabra, preguntas, conversaciones, 0, TendenciaDePalabra.NUEVA);
        }
    }

    /** 1.7.0: como le fue a una palabra contra el periodo anterior. */
    public enum TendenciaDePalabra {
        SUBE, BAJA, IGUAL, NUEVA;

        static TendenciaDePalabra de(long ahora, long antes) {
            if (antes == 0) {
                return NUEVA;
            }
            if (ahora > antes) {
                return SUBE;
            }
            return ahora < antes ? BAJA : IGUAL;
        }
    }

    /** 1.7.0: una pregunta repetida en al menos 3 conversaciones (D-CHA-06), ya normalizada. */
    public record PreguntaFrecuente(String texto, long preguntas, long conversaciones) {
    }

    /** 1.7.0: cuantas respuestas clasificadas terminaron de esa forma. */
    public record RespuestasPorResultado(ResultadoDeRespuesta resultado, long respuestas) {
    }
}
