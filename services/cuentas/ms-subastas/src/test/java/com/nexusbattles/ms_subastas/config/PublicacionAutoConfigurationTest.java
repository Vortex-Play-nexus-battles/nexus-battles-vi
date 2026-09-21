package com.nexusbattles.ms_subastas.config;

import com.nexusbattles.ms_subastas.subastas.port.*;
import com.nexusbattles.ms_subastas.subastas.service.*;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PublicacionAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PublicacionAutoConfiguration.class))
            .withBean(SubastaRepository.class, () -> mock(SubastaRepository.class))
            .withBean(InventarioClient.class, InventarioClientFake::new)
            .withBean(CatalogoProductosClient.class, () -> mock(CatalogoProductosClient.class))
            .withBean(IdentidadClient.class, () -> mock(IdentidadClient.class))
            .withBean(SancionesClient.class, () -> mock(SancionesClient.class))
            .withBean(IdempotenciaPublicacion.class, IdempotenciaPublicacionEnMemoria::new)
            .withBean(CalculadorComisionPublicacion.class, CalculadorComisionPublicacion::new)
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void sinFinanzasNoActivaPublicacion() {
        runner.run(context -> assertThat(context).doesNotHaveBean(PublicarSubastaApplicationService.class));
    }

    @Test
    void todosLosPuertosActivanElServicio() {
        runner.withBean(FinanzasPublicacionClient.class, () -> mock(FinanzasPublicacionClient.class))
                .withBean(InventarioPublicacionClient.class, () -> mock(InventarioPublicacionClient.class))
                .run(context -> assertThat(context).hasSingleBean(PublicarSubastaApplicationService.class));
    }

    @Test
    void finanzasYFakeDePujasNoActivanPublicacion() {
        runner.withBean(FinanzasPublicacionClient.class, () -> mock(FinanzasPublicacionClient.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(InventarioClient.class);
                    assertThat(context).doesNotHaveBean(PublicarSubastaApplicationService.class);
                });
    }

    @Test
    void clienteHttpSinCapacidadDePublicacionTampocoActiva() {
        runner.withBean(FinanzasPublicacionClient.class, () -> mock(FinanzasPublicacionClient.class))
                .withBean(InventarioClientHttp.class, () -> new InventarioClientHttp("http://localhost:8080", 1000,
                        new com.fasterxml.jackson.databind.ObjectMapper()))
                .run(context -> assertThat(context).doesNotHaveBean(PublicarSubastaApplicationService.class));
    }
}
