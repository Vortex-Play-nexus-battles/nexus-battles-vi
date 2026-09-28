package com.nexusbattles.plataforma.correo.cola;

import com.nexusbattles.plataforma.correo.envio.ComposicionDeCorreo;
import com.nexusbattles.plataforma.correo.envio.EnviadorCorreoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Quien vacia la cola en tiempo de ejecucion, y cuando no hay nadie.
 *
 * <p>El caso que importa es el de la variable vacia: una linea
 * {@code CORREO_ENTREGA_ACTIVA=} en el .env no puede dejar la cola sin
 * trabajador. Los correos se acumularian respondiendo 202 sin salir nunca.
 */
class ConfiguracionDeLaColaTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracionDeLaCola.class)
            .withBean(ConfiguracionDeEntrega.class, ConfiguracionDeEntrega::porOmision)
            .withBean(RepositorioDeEnvios.class, () -> mock(RepositorioDeEnvios.class))
            .withBean(EnviadorCorreoService.class, () -> mock(EnviadorCorreoService.class))
            .withBean(ComposicionDeCorreo.class, () -> mock(ComposicionDeCorreo.class))
            .withBean(MetricasDeCorreo.class, () -> mock(MetricasDeCorreo.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void porOmisionHayTrabajadorYRondasProgramadas() {
        contexto.run(arrancado -> {
            assertThat(arrancado).hasSingleBean(TrabajadorDeEntrega.class);
            assertThat(arrancado).hasSingleBean(ProgramacionDeEntrega.class);
            assertThat(arrancado.getBean(ScheduledTaskHolder.class).getScheduledTasks())
                    .as("la entrega y la purga")
                    .hasSize(2);
        });
    }

    @ParameterizedTest(name = "correo.entrega.activa=\"{0}\" sigue programando")
    @ValueSource(strings = {"true", "", " ", "cualquier-cosa"})
    void soloUnFalseExplicitoApagaLasRondas(String valor) {
        contexto.withPropertyValues("correo.entrega.activa=" + valor)
                .run(arrancado -> assertThat(arrancado).hasSingleBean(ProgramacionDeEntrega.class));
    }

    @ParameterizedTest(name = "correo.entrega.activa=\"{0}\" apaga las rondas")
    @ValueSource(strings = {"false", "FALSE", " false "})
    void conFalseElTrabajadorExistePeroNadieLoMueve(String valor) {
        contexto.withPropertyValues("correo.entrega.activa=" + valor)
                .run(arrancado -> {
                    assertThat(arrancado).hasSingleBean(TrabajadorDeEntrega.class);
                    assertThat(arrancado).doesNotHaveBean(ProgramacionDeEntrega.class);
                });
    }
}
