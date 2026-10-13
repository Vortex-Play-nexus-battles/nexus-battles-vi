package com.nexusbattles.ms_chatbot.chat.sugerencias;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// ms-chatbot.yaml 1.3.3: GET /chat/sugerencias. Publica (vive bajo /chat/**,
// en permitAll): es igual para visitantes y jugadores y no lee identidad.
@RestController
@RequestMapping("/chat/sugerencias")
public class SugerenciasController {

    private final SugerenciasService servicio;

    public SugerenciasController(SugerenciasService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<SugerenciaResponse> sugerir(@RequestParam(required = false) @Size(max = 100) String q,
                                            @RequestParam(required = false) Categoria categoria,
                                            @RequestParam(required = false) @Min(1) @Max(10) Integer limite) {
        return servicio.sugerir(q, categoria, limite).stream().map(SugerenciaResponse::desde).toList();
    }
}
