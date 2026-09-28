package com.nexusbattles.plataforma.correo.cola;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Las rondas programadas: con que intervalo y sin dejar escapar nada. */
class ProgramacionDeEntregaTest {

    private final TrabajadorDeEntrega trabajador = mock(TrabajadorDeEntrega.class);
    private final ConfiguracionDeEntrega configuracion = new ConfiguracionDeEntrega(
            true, 1500L, 10, 8, null, null, 30, 60_000L);

    @Test
    void registraLaEntregaYLaPurgaConLosIntervalosConfigurados() {
        ScheduledTaskRegistrar registro = new ScheduledTaskRegistrar();

        new ProgramacionDeEntrega(trabajador, configuracion).configureTasks(registro);

        List<IntervalTask> tareas = registro.getFixedDelayTaskList();
        assertThat(tareas).extracting(IntervalTask::getIntervalDuration)
                .containsExactly(Duration.ofMillis(1500), Duration.ofMillis(60_000));
        assertThat(tareas).extracting(IntervalTask::getInitialDelayDuration)
                .containsExactly(Duration.ofMillis(1500), Duration.ofMillis(60_000));

        tareas.get(0).getRunnable().run();
        tareas.get(1).getRunnable().run();
        verify(trabajador).procesarRonda();
        verify(trabajador).purgar();
    }

    @Test
    void unaBaseCaidaNoTumbaAlPlanificadorYSeAvisaUnaSolaVezPorRacha() {
        when(trabajador.procesarRonda())
                .thenThrow(new CannotGetJdbcConnectionException("caida"))
                .thenThrow(new CannotGetJdbcConnectionException("caida"))
                .thenReturn(0);
        ProgramacionDeEntrega programacion = new ProgramacionDeEntrega(trabajador, configuracion);

        assertThatCode(programacion::entregar).doesNotThrowAnyException();
        assertThat(programacion.enFallo()).isTrue();
        assertThatCode(programacion::entregar).doesNotThrowAnyException();
        assertThat(programacion.enFallo()).isTrue();

        programacion.entregar();
        assertThat(programacion.enFallo()).as("al volver la base, se sale de la racha").isFalse();
        verify(trabajador, times(3)).procesarRonda();
    }

    @Test
    void unaPurgaFallidaTampocoEscapa() {
        when(trabajador.purgar()).thenThrow(new CannotGetJdbcConnectionException("caida"));

        assertThatCode(new ProgramacionDeEntrega(trabajador, configuracion)::purgar).doesNotThrowAnyException();
    }
}
