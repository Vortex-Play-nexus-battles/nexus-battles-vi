package com.nexusbattles.ms_chatbot.chat.conocimiento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// ms-chatbot.yaml 1.3.8: la tarea que publica la candidata programada.
@ExtendWith(MockitoExtension.class)
class DespliegueProgramadoTareaTest {

    @Mock
    private EvaluacionBaseConocimientoService evaluacionService;

    @InjectMocks
    private DespliegueProgramadoTarea tarea;

    @Test
    void revisar_desplegada_informaSinLanzar() {
        when(evaluacionService.desplegarSiCorresponde(any())).thenReturn(Optional.of(DespliegueProgramado.desplegada(3)));

        assertThatCode(() -> tarea.revisar()).doesNotThrowAnyException();
        verify(evaluacionService).desplegarSiCorresponde(any());
    }

    @Test
    void revisar_rechazada_informaSinLanzar() {
        when(evaluacionService.desplegarSiCorresponde(any()))
            .thenReturn(Optional.of(DespliegueProgramado.rechazada(3, "Rinde peor.")));

        assertThatCode(() -> tarea.revisar()).doesNotThrowAnyException();
    }

    @Test
    void revisar_sinNadaQueHacer_noLanza() {
        when(evaluacionService.desplegarSiCorresponde(any())).thenReturn(Optional.empty());

        assertThatCode(() -> tarea.revisar()).doesNotThrowAnyException();
    }

    // Una falla se registra y la tarea sigue: no llega al planificador.
    @Test
    void revisar_siElServicioFalla_noPropagaLaExcepcion() {
        when(evaluacionService.desplegarSiCorresponde(any())).thenThrow(new IllegalStateException("base caida"));

        assertThatCode(() -> tarea.revisar()).doesNotThrowAnyException();
    }
}
