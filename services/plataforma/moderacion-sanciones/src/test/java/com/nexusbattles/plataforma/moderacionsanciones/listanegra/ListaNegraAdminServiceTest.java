package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListaNegraAdminServiceTest {

    @Mock
    private TerminoProhibidoRepository repository;

    @Test
    void listaLosTerminosExistentes() {
        when(repository.findAll()).thenReturn(List.of(
                new TerminoProhibido("malapalabra"),
                new TerminoProhibido("insulto")
        ));
        ListaNegraAdminService service = new ListaNegraAdminService(repository);

        List<String> resultado = service.listarTerminos();

        assertThat(resultado).containsExactly("malapalabra", "insulto");
    }

    @Test
    void agregaUnTerminoNuevo() {
        when(repository.existsByTerminoIgnoreCase("malapalabra")).thenReturn(false);
        ListaNegraAdminService service = new ListaNegraAdminService(repository);

        service.agregarTermino("malapalabra");

        verify(repository).save(any(TerminoProhibido.class));
    }

    @Test
    void noDuplicaUnTerminoYaExistente() {
        when(repository.existsByTerminoIgnoreCase("malapalabra")).thenReturn(true);
        ListaNegraAdminService service = new ListaNegraAdminService(repository);

        service.agregarTermino("malapalabra");

        verify(repository, never()).save(any(TerminoProhibido.class));
    }

    @Test
    void rechazaUnTerminoVacio() {
        ListaNegraAdminService service = new ListaNegraAdminService(repository);

        assertThatThrownBy(() -> service.agregarTermino("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eliminaUnTerminoExistente() {
        TerminoProhibido existente = new TerminoProhibido("malapalabra");
        when(repository.findByTerminoIgnoreCase("malapalabra")).thenReturn(Optional.of(existente));
        ListaNegraAdminService service = new ListaNegraAdminService(repository);

        service.eliminarTermino("malapalabra");

        verify(repository).delete(existente);
    }

    @Test
    void lanzaExcepcionAlEliminarUnTerminoQueNoExiste() {
        when(repository.findByTerminoIgnoreCase("fantasma")).thenReturn(Optional.empty());
        ListaNegraAdminService service = new ListaNegraAdminService(repository);

        assertThatThrownBy(() -> service.eliminarTermino("fantasma"))
                .isInstanceOf(TerminoNoEncontradoException.class);
    }

    @Test
    void editaUnTerminoExistente() {
        TerminoProhibido existente = new TerminoProhibido("malapalabra");
        when(repository.findByTerminoIgnoreCase("malapalabra")).thenReturn(Optional.of(existente));
        ListaNegraAdminService service = new ListaNegraAdminService(repository);

        service.editarTermino("malapalabra", "peorpalabra");

        assertThat(existente.getTermino()).isEqualTo("peorpalabra");
        verify(repository).save(existente);
    }

    @Test
    void lanzaExcepcionAlEditarUnTerminoQueNoExiste() {
        when(repository.findByTerminoIgnoreCase("fantasma")).thenReturn(Optional.empty());
        ListaNegraAdminService service = new ListaNegraAdminService(repository);

        assertThatThrownBy(() -> service.editarTermino("fantasma", "nuevo"))
                .isInstanceOf(TerminoNoEncontradoException.class);
    }
}
