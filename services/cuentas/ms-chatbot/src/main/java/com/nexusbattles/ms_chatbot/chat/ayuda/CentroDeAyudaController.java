package com.nexusbattles.ms_chatbot.chat.ayuda;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

// ms-chatbot.yaml 1.8.0: GET /chat/ayuda. Publica (vive bajo /chat/**, en
// permitAll) y sin identidad: es igual para todos, asi que se puede guardar
// en cache cinco minutos. Spring Security no pisa un Cache-Control que la
// respuesta ya trae.
@RestController
@RequestMapping("/chat/ayuda")
public class CentroDeAyudaController {

    static final Duration EN_CACHE = Duration.ofMinutes(5);

    private final CentroDeAyudaService servicio;

    public CentroDeAyudaController(CentroDeAyudaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<CentroDeAyudaResponse> centro() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(EN_CACHE).cachePublic())
            .body(servicio.centro());
    }
}
