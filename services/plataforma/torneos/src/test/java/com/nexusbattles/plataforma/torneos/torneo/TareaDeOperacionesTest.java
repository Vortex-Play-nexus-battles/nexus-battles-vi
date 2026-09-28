package com.nexusbattles.plataforma.torneos.torneo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Torneos · tarea programada de operaciones")
class TareaDeOperacionesTest {

    @Test
    @DisplayName("cada vuelta procesa lo pendiente y un fallo no mata la tarea")
    void vueltas() {
        ProcesadorDeOperaciones procesador = mock(ProcesadorDeOperaciones.class);
        when(procesador.procesarPendientes()).thenReturn(3).thenReturn(0).thenThrow(new IllegalStateException("base caida"));
        TareaDeOperaciones tarea = new TareaDeOperaciones(procesador);

        tarea.reintentar();
        tarea.reintentar();
        assertThatCode(tarea::reintentar).doesNotThrowAnyException();
        verify(procesador, times(3)).procesarPendientes();
    }
}
