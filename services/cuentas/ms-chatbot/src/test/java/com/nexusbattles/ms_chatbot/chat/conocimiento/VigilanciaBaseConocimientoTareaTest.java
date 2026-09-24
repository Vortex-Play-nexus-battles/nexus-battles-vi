package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.conocimiento.ResultadoEvaluacion.FalloDeCaso;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VigilanciaBaseConocimientoTareaTest {

    @Mock
    private EvaluacionBaseConocimientoService evaluacionService;

    @InjectMocks
    private VigilanciaBaseConocimientoTarea tarea;

    @Test
    void evaluarProduccion_degradada_informaSinLanzar() {
        when(evaluacionService.vigilarProduccion())
            .thenReturn(Optional.of(VigilanciaProduccion.de(resultado(2, 3), 1.0)));

        assertThatCode(() -> tarea.evaluarProduccion()).doesNotThrowAnyException();
        verify(evaluacionService).vigilarProduccion();
    }

    @Test
    void evaluarProduccion_estable_informaSinLanzar() {
        when(evaluacionService.vigilarProduccion())
            .thenReturn(Optional.of(VigilanciaProduccion.de(resultado(3, 3), 1.0)));

        assertThatCode(() -> tarea.evaluarProduccion()).doesNotThrowAnyException();
    }

    @Test
    void evaluarProduccion_sinCasos_noLanza() {
        when(evaluacionService.vigilarProduccion()).thenReturn(Optional.empty());

        assertThatCode(() -> tarea.evaluarProduccion()).doesNotThrowAnyException();
    }

    // Si la evaluacion falla (p. ej. no hay version en produccion), la tarea
    // lo registra y sigue: no debe propagar la excepcion al planificador.
    @Test
    void evaluarProduccion_siElServicioFalla_noPropagaLaExcepcion() {
        when(evaluacionService.vigilarProduccion())
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "No hay una version en produccion."));

        assertThatCode(() -> tarea.evaluarProduccion()).doesNotThrowAnyException();
    }

    @Test
    void vigilancia_soloEsDegradadaSiLaTasaBaja() {
        assertThat(VigilanciaProduccion.de(resultado(2, 3), 1.0).degradada()).isTrue();
        assertThat(VigilanciaProduccion.de(resultado(3, 3), 1.0).degradada()).isFalse();
        assertThat(VigilanciaProduccion.de(resultado(3, 3), 0.5).degradada()).isFalse();
        assertThat(VigilanciaProduccion.de(resultado(2, 3), null).degradada()).isFalse();
    }

    private static ResultadoEvaluacion resultado(int aciertos, int casos) {
        List<FalloDeCaso> fallos = aciertos == casos
            ? List.of()
            : List.of(new FalloDeCaso(UUID.randomUUID(), "como cambiar contrasena", "clave-contrasena", null));
        return new ResultadoEvaluacion(UUID.randomUUID(), 1, casos, aciertos, (double) aciertos / casos, fallos);
    }
}
