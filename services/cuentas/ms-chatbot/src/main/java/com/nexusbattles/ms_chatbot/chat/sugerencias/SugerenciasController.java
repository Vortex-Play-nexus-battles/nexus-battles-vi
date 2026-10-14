package com.nexusbattles.ms_chatbot.chat.sugerencias;

import com.nexusbattles.ms_chatbot.chat.api.ChatController;
import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// ms-chatbot.yaml 1.3.3: GET /chat/sugerencias. Publica (vive bajo /chat/**,
// en permitAll): es igual para visitantes y jugadores y no lee identidad.
//
// 1.15.0 (7.4.3 «sugerencias proactivas»): GET /chat/sugerencias/para-ti, las
// de quien pregunta, con la misma identidad que el chat (el uid del token o
// una sesion de visitante registrada; nunca se emite una aqui). Sin
// identidad, solo las de la seccion y las de quien empieza.
@RestController
@RequestMapping("/chat/sugerencias")
public class SugerenciasController {

    private final SugerenciasService servicio;
    private final SugerenciasParaTiService paraTi;
    private final ResolutorDeIdentidad resolutor;

    public SugerenciasController(SugerenciasService servicio, SugerenciasParaTiService paraTi,
                                 ResolutorDeIdentidad resolutor) {
        this.servicio = servicio;
        this.paraTi = paraTi;
        this.resolutor = resolutor;
    }

    @GetMapping("/para-ti")
    public List<SugerenciaParaTiResponse> paraTi(@RequestParam(required = false) VistaDelChat vista,
                                                 @RequestParam(required = false) @Min(1) @Max(5) Integer limite,
                                                 Authentication authentication,
                                                 @RequestHeader(value = ChatController.CABECERA_SESION_ANONIMA,
                                                     required = false) String sesion) {
        IdentidadDelChat quien = resolutor.resolver(authentication, sesion).orElse(null);
        return paraTi.para(quien, vista, limite).stream().map(SugerenciaParaTiResponse::desde).toList();
    }

    @GetMapping
    public List<SugerenciaResponse> sugerir(@RequestParam(required = false) @Size(max = 100) String q,
                                            @RequestParam(required = false) Categoria categoria,
                                            @RequestParam(required = false) @Min(1) @Max(10) Integer limite) {
        return servicio.sugerir(q, categoria, limite).stream().map(SugerenciaResponse::desde).toList();
    }
}
