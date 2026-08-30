package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListaNegraAdminService {

    static final String CACHE_TERMINOS = "terminosProhibidos";

    private final TerminoProhibidoRepository repository;

    public ListaNegraAdminService(TerminoProhibidoRepository repository) {
        this.repository = repository;
    }

    @Cacheable(CACHE_TERMINOS)
    public List<String> listarTerminos() {
        return repository.findAll().stream()
                .map(TerminoProhibido::getTermino)
                .toList();
    }

    @CacheEvict(value = CACHE_TERMINOS, allEntries = true)
    public void agregarTermino(String termino) {
        if (termino == null || termino.isBlank()) {
            throw new IllegalArgumentException("El termino no puede estar vacio");
        }
        if (repository.existsByTerminoIgnoreCase(termino)) {
            return;
        }
        repository.save(new TerminoProhibido(termino.trim().toLowerCase()));
    }

    @CacheEvict(value = CACHE_TERMINOS, allEntries = true)
    public void eliminarTermino(String termino) {
        TerminoProhibido existente = repository.findByTerminoIgnoreCase(termino)
                .orElseThrow(() -> new TerminoNoEncontradoException(termino));
        repository.delete(existente);
    }

    @CacheEvict(value = CACHE_TERMINOS, allEntries = true)
    public void editarTermino(String terminoActual, String terminoNuevo) {
        if (terminoNuevo == null || terminoNuevo.isBlank()) {
            throw new IllegalArgumentException("El termino no puede estar vacio");
        }
        TerminoProhibido existente = repository.findByTerminoIgnoreCase(terminoActual)
                .orElseThrow(() -> new TerminoNoEncontradoException(terminoActual));
        existente.actualizarTermino(terminoNuevo.trim().toLowerCase());
        repository.save(existente);
    }
}
