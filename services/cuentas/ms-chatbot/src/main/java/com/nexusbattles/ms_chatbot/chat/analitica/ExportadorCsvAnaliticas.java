package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PalabraClave;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PreguntaFrecuente;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PuntoDeTendencia;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.RespuestasPorResultado;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.ResumenDeTickets;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.TemaFrecuente;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.TicketsPorCategoria;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

// HU-CHA-012: exportacion del tablero de analiticas ("exportables", RF-CHA-012).
//
// Formato pensado para abrirlo directo en Excel en espanol:
//   - separador ';' (el separador de listas de Excel en es-CO; con ',' todo
//     quedaria en una sola columna);
//   - UTF-8 con BOM (sin la marca, Excel muestra mal las tildes);
//   - lineas CRLF (RFC 4180) y celdas con ';', comillas o saltos de linea
//     entre comillas dobles.
// Bloques separados por una linea en blanco: resumen, temas frecuentes,
// tendencia diaria, desde 1.3.7 solicitudes de soporte y palabras clave, y
// desde 1.7.0 resultado de las respuestas y preguntas frecuentes.
//
// 1.7.0 — sin formulas (inyeccion CSV, OWASP): las preguntas, las palabras
// clave y los titulos los escriben personas, y una celda que empieza por '=',
// '+', '-', '@', tabulador o retorno de carro la hoja de calculo la ejecuta
// como formula ('=HYPERLINK(...)', '@SUM(...)'). Esas celdas llevan delante
// una comilla simple, que la hoja muestra como texto; despues se escapan las
// comillas y el separador como siempre.
final class ExportadorCsvAnaliticas {

    private static final String SEPARADOR = ";";
    private static final String FIN_DE_LINEA = "\r\n";
    private static final String MARCA_BOM = "﻿";
    private static final String INICIOS_DE_FORMULA = "=+-@\t\r";

    private ExportadorCsvAnaliticas() {
    }

    static String aCsv(AnaliticaChatbot analitica, LocalDate desde, LocalDate hasta) {
        StringBuilder csv = new StringBuilder(MARCA_BOM);

        fila(csv, "Analiticas del chatbot", desde + " a " + hasta + " (" + analitica.zonaHoraria() + ")");
        csv.append(FIN_DE_LINEA);

        ResumenDeTickets tickets = analitica.tickets() == null ? ResumenDeTickets.VACIO : analitica.tickets();
        fila(csv, "Indicador", "Valor");
        fila(csv, "Conversaciones iniciadas", analitica.conversaciones());
        fila(csv, "Conversaciones con preguntas", analitica.conversacionesConActividad());
        fila(csv, "Preguntas", analitica.preguntas());
        fila(csv, "Respuestas medidas", analitica.respuestasMedidas());
        fila(csv, "Respuestas con resultado", analitica.respuestasClasificadas());
        fila(csv, "Respuestas resueltas sin intervencion humana", analitica.respuestasResueltas());
        fila(csv, "Tasa de resolucion (%)", porcentaje(analitica.tasaResolucion()));
        fila(csv, "Veces que el asistente ofrecio soporte humano", analitica.escalamientos());
        fila(csv, "Solicitudes de soporte abiertas", tickets.total());
        fila(csv, "Tiempo de respuesta promedio (ms)", entero(analitica.tiempoRespuestaPromedioMs()));
        fila(csv, "Calificaciones", analitica.calificaciones());
        fila(csv, "Calificaciones utiles", analitica.calificacionesUtiles());
        fila(csv, "Satisfaccion (%)", porcentaje(analitica.satisfaccion()));
        csv.append(FIN_DE_LINEA);

        fila(csv, "Resultado de las respuestas");
        fila(csv, "Resultado", "Respuestas", "Cuenta en la tasa como");
        for (RespuestasPorResultado resultado : lista(analitica.resultados())) {
            fila(csv, resultado.resultado(), resultado.respuestas(),
                resultado.resultado().resuelve() ? "Resuelta" : "No resuelta");
        }
        csv.append(FIN_DE_LINEA);

        fila(csv, "Temas frecuentes");
        fila(csv, "Clave", "Tema", "Respuestas");
        for (TemaFrecuente tema : analitica.temasFrecuentes()) {
            fila(csv, tema.clave(), tema.titulo(), tema.respuestas());
        }
        csv.append(FIN_DE_LINEA);

        fila(csv, "Preguntas frecuentes");
        fila(csv, "Pregunta", "Veces", "Conversaciones");
        for (PreguntaFrecuente pregunta : lista(analitica.preguntasFrecuentes())) {
            fila(csv, pregunta.texto(), pregunta.preguntas(), pregunta.conversaciones());
        }
        csv.append(FIN_DE_LINEA);

        fila(csv, "Tendencia diaria");
        fila(csv, "Dia", "Conversaciones iniciadas", "Preguntas", "Respuestas", "Escalamientos");
        for (PuntoDeTendencia punto : analitica.tendencia()) {
            fila(csv, punto.dia(), punto.conversaciones(), punto.preguntas(), punto.respuestas(), punto.escalamientos());
        }
        csv.append(FIN_DE_LINEA);

        fila(csv, "Solicitudes de soporte");
        fila(csv, "Indicador", "Valor");
        fila(csv, "Abiertas en el periodo", tickets.total());
        fila(csv, "Sin atender", tickets.abiertos());
        fila(csv, "En revision", tickets.enProceso());
        fila(csv, "Respondidas", tickets.resueltos());
        fila(csv, "Cerradas", tickets.cerrados());
        fila(csv, "Horas promedio de atencion", decimal(tickets.horasPromedioDeAtencion()));
        fila(csv, "Categoria", "Solicitudes");
        for (TicketsPorCategoria categoria : tickets.porCategoria()) {
            fila(csv, categoria.categoria(), categoria.tickets());
        }
        csv.append(FIN_DE_LINEA);

        fila(csv, "Palabras clave");
        fila(csv, "Palabra", "Preguntas", "Conversaciones", "Preguntas en el periodo anterior", "Tendencia");
        for (PalabraClave palabra : lista(analitica.palabrasClave())) {
            fila(csv, palabra.palabra(), palabra.preguntas(), palabra.conversaciones(),
                palabra.preguntasPeriodoAnterior(), palabra.tendencia());
        }

        return csv.toString();
    }

    private static <T> List<T> lista(List<T> valores) {
        return valores == null ? List.of() : valores;
    }

    private static void fila(StringBuilder csv, Object... celdas) {
        for (int i = 0; i < celdas.length; i++) {
            if (i > 0) {
                csv.append(SEPARADOR);
            }
            csv.append(escapar(celdas[i]));
        }
        csv.append(FIN_DE_LINEA);
    }

    // Primero se desarma la formula y despues se entrecomilla: la comilla
    // simple queda DENTRO de las comillas dobles, que es como Excel la lee.
    static String escapar(Object valor) {
        String texto = sinFormula(valor == null ? "" : valor.toString());
        if (texto.contains(SEPARADOR) || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }

    static String sinFormula(String texto) {
        if (!texto.isEmpty() && INICIOS_DE_FORMULA.indexOf(texto.charAt(0)) >= 0) {
            return "'" + texto;
        }
        return texto;
    }

    // Vacio cuando no hay datos, igual que el null del JSON: "sin datos" no es 0 %.
    private static String porcentaje(Double proporcion) {
        return proporcion == null ? "" : String.format(Locale.ROOT, "%.1f", proporcion * 100);
    }

    private static String decimal(Double valor) {
        return valor == null ? "" : String.format(Locale.ROOT, "%.1f", valor);
    }

    private static String entero(Double valor) {
        return valor == null ? "" : String.format(Locale.ROOT, "%.0f", valor);
    }
}
