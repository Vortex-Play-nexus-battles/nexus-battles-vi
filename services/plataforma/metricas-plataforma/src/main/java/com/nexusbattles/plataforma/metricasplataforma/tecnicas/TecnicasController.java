package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/tecnicas} (JSON) y {@code /api/v1/tecnicas/informe/texto}
 * — HU-MET-004. Sin logica: la recoleccion, sus plazos y su vigencia viven en
 * {@link TableroEnVivo}.
 */
@RestController
@RequestMapping("/api/v1/tecnicas")
public class TecnicasController {

    private final TableroEnVivo tablero;

    public TecnicasController(TableroEnVivo tablero) {
        this.tablero = tablero;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public TableroTecnico tablero() {
        return tablero.actual();
    }

    @GetMapping(value = "/informe/texto", produces = MediaType.TEXT_PLAIN_VALUE)
    public String texto() {
        return tablero.actual().comoTexto();
    }
}
