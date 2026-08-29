package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificacionListaNegraServiceTest {

    @Mock
    private ListaNegraAdminService listaNegraAdminService;

    private final DetectorTerminosProhibidosService detector = new DetectorTerminosProhibidosService();

    @Test
    void apruebaUnTextoLimpio() {
        when(listaNegraAdminService.listarTerminos()).thenReturn(List.of("malapalabra", "insulto"));
        VerificacionListaNegraService service = new VerificacionListaNegraService(listaNegraAdminService, detector);

        var resultado = service.verificar("hola, este es un texto normal");

        assertThat(resultado.aprobado()).isTrue();
        assertThat(resultado.motivo()).isNull();
    }

    @Test
    void rechazaUnTextoConTerminoProhibido() {
        when(listaNegraAdminService.listarTerminos()).thenReturn(List.of("malapalabra", "insulto"));
        VerificacionListaNegraService service = new VerificacionListaNegraService(listaNegraAdminService, detector);

        var resultado = service.verificar("esto tiene una malapalabra");

        assertThat(resultado.aprobado()).isFalse();
        assertThat(resultado.motivo()).isNotBlank();
    }

    @Test
    void rechazaUnTextoVacioConError() {
        VerificacionListaNegraService service = new VerificacionListaNegraService(listaNegraAdminService, detector);

        assertThatThrownBy(() -> service.verificar("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
