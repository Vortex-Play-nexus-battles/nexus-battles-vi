package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PuntoDeTendencia;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.TemaFrecuente;

import java.time.LocalDate;
import java.util.Locale;

// HU-CHA-012: exportacion del tablero de analiticas ("exportables", RF-CHA-012).
//
// Formato pensado para abrirlo directo en Excel en espanol:
//   - separador ';' (el separador de listas de Excel en es-CO; con ',' todo
//     quedaria en una sola columna);
//   - UTF-8 con BOM (sin la marca, Excel muestra mal las tildes);
//   - lineas CRLF (RFC 4180) y celdas con ';', comillas o saltos de linea
//     entre comillas dobles.
// Tres bloques separados por una linea en blanco: resumen, temas frecuentes y
// tendencia diaria.
final class ExportadorCsvAnaliticas {

    private static final String SEPARADOR = ";";
    private static final String FIN_DE_LINEA = "\r\n";
    private static final String MARCA_BOM = "\uFEFF";

    private ExportadorCsvAnaliticas() {
    }

    static String aCsv(AnaliticaChatbot analitica, LocalDate desde, LocalDate hasta) {
        StringBuilder csv = new StringBuilder(MARCA_BOM);

        fila(csv, "Analiticas del chatbot", desde + " a " + hasta + " (" + analitica.zonaHoraria() + ")");
        csv.append(FIN_DE_LINEA);

        fila(csv, "Indicador", "Valor");
        fila(csv, "Conversaciones", analitica.conversaciones());
        fila(csv, "Preguntas", analitica.preguntas());
        fila(csv, "Respuestas medidas", analitica.respuestasMedidas());
        fila(csv, "Escalamientos", analitica.escalamientos());
        fila(csv, "Tasa de resolucion (%)", porcentaje(analitica.tasaResolucion()));
        fila(csv, "Tiempo de respuesta promedio (ms)", entero(analitica.tiempoRespuestaPromedioMs()));
        fila(csv, "Calificaciones", analitica.calificaciones());
        fila(csv, "Calificaciones utiles", analitica.calificacionesUtiles());
        fila(csv, "Satisfaccion (%)", porcentaje(analitica.satisfaccion()));
        csv.append(FIN_DE_LINEA);

        fila(csv, "Temas frecuentes");
        fila(csv, "Clave", "Tema", "Respuestas");
        for (TemaFrecuente tema : analitica.temasFrecuentes()) {
            fila(csv, tema.clave(), tema.titulo(), tema.respuestas());
        }
        csv.append(FIN_DE_LINEA);

        fila(csv, "Tendencia diaria");
        fila(csv, "Dia", "Conversaciones", "Preguntas", "Respuestas", "Escalamientos");
        for (PuntoDeTendencia punto : analitica.tendencia()) {
            fila(csv, punto.dia(), punto.conversaciones(), punto.preguntas(), punto.respuestas(), punto.escalamientos());
        }

        return csv.toString();
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

    private static String escapar(Object valor) {
        String texto = valor == null ? "" : valor.toString();
        if (texto.contains(SEPARADOR) || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }

    // Vacio cuando no hay datos, igual que el null del JSON: "sin datos" no es 0 %.
    private static String porcentaje(Double proporcion) {
        return proporcion == null ? "" : String.format(Locale.ROOT, "%.1f", proporcion * 100);
    }

    private static String entero(Double valor) {
        return valor == null ? "" : String.format(Locale.ROOT, "%.0f", valor);
    }
}
