package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PalabraClave;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PreguntaFrecuente;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PuntoDeTendencia;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.RespuestasPorResultado;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.ResumenDeTickets;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.TemaFrecuente;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.TendenciaDePalabra;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ms-chatbot.yaml 1.7.0: la exportacion CSV no lleva formulas (inyeccion CSV)
// y trae los bloques nuevos.
class ExportadorCsvAnaliticasTest {

    private static final LocalDate DIA = LocalDate.of(2026, 9, 9);

    // Lo que escribiria alguien que quiere que Excel ejecute algo al abrir el
    // archivo: cada celda que empieza por = + - @ tabulador o retorno de carro.
    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(\"http://x.example\",\"pulsa\")", "+1+1", "-2+3", "@SUM(A1:A2)",
        "\tcmd", "\r=1"})
    void unaCeldaQueEmpiezaComoFormula_llevaComillaSimpleDelante(String malicioso) {
        assertThat(ExportadorCsvAnaliticas.sinFormula(malicioso)).isEqualTo("'" + malicioso);
        assertThat(ExportadorCsvAnaliticas.escapar(malicioso)).doesNotStartWith("=").doesNotStartWith("+")
            .doesNotStartWith("-").doesNotStartWith("@").doesNotStartWith("\t").doesNotStartWith("\r");
    }

    // La comilla simple va DENTRO de las comillas dobles del CSV: primero se
    // desarma la formula y despues se escapa como siempre.
    @Test
    void laComillaSimpleQuedaDentroDelEntrecomillado() {
        assertThat(ExportadorCsvAnaliticas.escapar("=1;2")).isEqualTo("\"'=1;2\"");
        assertThat(ExportadorCsvAnaliticas.escapar("=\"x\"")).isEqualTo("\"'=\"\"x\"\"\"");
        assertThat(ExportadorCsvAnaliticas.escapar("\r=1")).isEqualTo("\"'\r=1\"");
    }

    @Test
    void loNormalNoCambia() {
        assertThat(ExportadorCsvAnaliticas.escapar("como pujo")).isEqualTo("como pujo");
        assertThat(ExportadorCsvAnaliticas.escapar("a=b")).isEqualTo("a=b");
        assertThat(ExportadorCsvAnaliticas.escapar("")).isEmpty();
        assertThat(ExportadorCsvAnaliticas.escapar(null)).isEmpty();
        assertThat(ExportadorCsvAnaliticas.escapar(12L)).isEqualTo("12");
        assertThat(ExportadorCsvAnaliticas.escapar("2026-09-09")).isEqualTo("2026-09-09");
    }

    // De punta a punta: preguntas, palabras y titulos maliciosos salen como
    // texto en el archivo, y los bloques nuevos de 1.7.0 estan.
    @Test
    void elArchivoNoLlevaFormulasEnNingunBloque() {
        AnaliticaChatbot analitica = new AnaliticaChatbot(
            Instant.parse("2026-09-09T05:00:00Z"), Instant.parse("2026-09-10T05:00:00Z"), "America/Bogota",
            1, 2, 3, 4, 1, 4, 3, 0.75,
            List.of(new RespuestasPorResultado(ResultadoDeRespuesta.TEMA, 3),
                new RespuestasPorResultado(ResultadoDeRespuesta.ESCALADO, 1)),
            120.0, 2, 1, 0.5,
            List.of(new TemaFrecuente("clave", "=cmd|' /C calc'!A0", 3)),
            List.of(new PreguntaFrecuente("@SUM(1+1)*cmd|' /C calc'!A0", 3, 3)),
            List.of(new PuntoDeTendencia(DIA, 1, 3, 4, 1)),
            ResumenDeTickets.VACIO,
            List.of(new PalabraClave("-2+3", 2, 2, 1, TendenciaDePalabra.SUBE)));

        String csv = ExportadorCsvAnaliticas.aCsv(analitica, DIA, DIA);

        assertThat(csv)
            .contains(";'=cmd|' /C calc'!A0;3")
            .contains("'@SUM(1+1)*cmd|' /C calc'!A0;3;3")
            .contains("'-2+3;2;2;1;SUBE")
            .contains("Conversaciones iniciadas;1")
            .contains("Conversaciones con preguntas;2")
            .contains("Tasa de resolucion (%);75.0")
            .contains("Veces que el asistente ofrecio soporte humano;1")
            .contains("Respuestas con resultado;4")
            .contains("Respuestas resueltas sin intervencion humana;3")
            .contains("Solicitudes de soporte abiertas;0")
            .contains("Resultado de las respuestas")
            .contains("TEMA;3;Resuelta")
            .contains("ESCALADO;1;No resuelta")
            .contains("Preguntas frecuentes")
            .contains("2026-09-09;1;3;4;1");
        // Ninguna celda del archivo empieza por un caracter de formula.
        for (String linea : csv.substring(1).split("\r\n")) {
            for (String celda : linea.split(";")) {
                assertThat(celda).as(linea).doesNotStartWith("=").doesNotStartWith("+").doesNotStartWith("@");
                if (!celda.isEmpty() && celda.charAt(0) == '-') {
                    throw new AssertionError("celda que empieza por '-': " + linea);
                }
            }
        }
    }
}
