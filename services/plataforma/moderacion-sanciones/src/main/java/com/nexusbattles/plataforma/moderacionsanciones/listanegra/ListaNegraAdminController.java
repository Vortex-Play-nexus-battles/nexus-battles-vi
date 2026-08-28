package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lista-negra/terminos")
public class ListaNegraAdminController {

    private final ListaNegraAdminService service;

    public ListaNegraAdminController(ListaNegraAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<String> listarTerminos() {
        return service.listarTerminos();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void agregarTermino(@RequestBody TerminoRequest request) {
        service.agregarTermino(request.termino());
    }

    @PutMapping("/{termino}")
    public void editarTermino(@PathVariable String termino, @RequestBody TerminoRequest request) {
        service.editarTermino(termino, request.termino());
    }

    @DeleteMapping("/{termino}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarTermino(@PathVariable String termino) {
        service.eliminarTermino(termino);
    }

    public record TerminoRequest(String termino) {
    }
}
