package com.nexusbattles.ms_chatbot.chat.analitica;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

// HU-CHA-012 (RF-CHA-012): tablero de analiticas del chatbot para el panel
// administrativo. Solo ADMINISTRADOR y SUPER_ADMINISTRADOR (SecurityConfig,
// regla de /chatbot/admin/**).
//
// El periodo son dias completos en hora de Colombia, 'desde' y 'hasta'
// inclusive (formato ISO, 2026-09-30). Sin fechas: los ultimos 30 dias,
// terminando hoy.
@RestController
@RequestMapping("/chatbot/admin/analiticas")
public class AnaliticaChatbotController {

    static final int DIAS_POR_DEFECTO = 30;

    private static final MediaType TEXTO_CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final AnaliticaChatbotService analiticaChatbotService;

    public AnaliticaChatbotController(AnaliticaChatbotService analiticaChatbotService) {
        this.analiticaChatbotService = analiticaChatbotService;
    }

    @GetMapping
    public AnaliticaChatbot consultar(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        Periodo periodo = Periodo.resolver(desde, hasta);
        return analiticaChatbotService.calcular(periodo.desde(), periodo.hasta(),
            AnaliticaChatbotService.ZONA_POR_DEFECTO);
    }

    @GetMapping("/exportacion")
    public ResponseEntity<String> exportar(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        Periodo periodo = Periodo.resolver(desde, hasta);
        AnaliticaChatbot analitica = analiticaChatbotService.calcular(periodo.desde(), periodo.hasta(),
            AnaliticaChatbotService.ZONA_POR_DEFECTO);

        String nombreArchivo = "analiticas-chatbot-" + periodo.desde() + "-a-" + periodo.hasta() + ".csv";
        return ResponseEntity.ok()
            .contentType(TEXTO_CSV)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(nombreArchivo).build().toString())
            .body(ExportadorCsvAnaliticas.aCsv(analitica, periodo.desde(), periodo.hasta()));
    }

    private record Periodo(LocalDate desde, LocalDate hasta) {

        static Periodo resolver(LocalDate desde, LocalDate hasta) {
            LocalDate fin = hasta != null ? hasta : LocalDate.now(AnaliticaChatbotService.ZONA_POR_DEFECTO);
            LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO - 1L);
            return new Periodo(inicio, fin);
        }
    }
}
