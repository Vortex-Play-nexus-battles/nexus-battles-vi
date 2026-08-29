package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lista-negra")
public class ListaNegraVerificacionController {

    private final VerificacionListaNegraService service;

    public ListaNegraVerificacionController(VerificacionListaNegraService service) {
        this.service = service;
    }

    @PostMapping("/verificar")
    public VerificacionListaNegraResponse verificar(@RequestBody VerificacionListaNegraRequest request) {
        var resultado = service.verificar(request.texto());
        return new VerificacionListaNegraResponse(resultado.aprobado(), resultado.motivo());
    }

    public record VerificacionListaNegraRequest(String texto) {
    }

    public record VerificacionListaNegraResponse(boolean aprobado, String motivo) {
    }
}
