package com.nexusbattles.ms_subastas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
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
            .withUserConfiguration(InventarioClientConfig.class)
            .withConfiguration(AutoConfigurations.of(PublicacionAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(TokenDeServicio.class, () -> () -> "token-servicio-prueba")
            .withBean(SubastaRepository.class, () -> mock(SubastaRepository.class))
            .withBean(CatalogoProductosClient.class, () -> mock(CatalogoProductosClient.class))
            .withBean(IdentidadClient.class, () -> mock(IdentidadClient.class))
            .withBean(SancionesClient.class, () -> mock(SancionesClient.class))
            .withBean(IdempotenciaPublicacion.class, IdempotenciaPublicacionEnMemoria::new)
            .withBean(CalculadorComisionPublicacion.class, CalculadorComisionPublicacion::new)
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void httpComparteUnSoloClienteYActivaPublicacion() {
        runner.withPropertyValues("app.inventario.modo=http", "app.subastas.incremento-minimo=1")
                .withUserConfiguration(FinanzasPublicacionClientHttp.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(InventarioClient.class)
                            .hasSingleBean(InventarioPublicacionClient.class)
                            .hasSingleBean(PublicarSubastaApplicationService.class)
                            .hasSingleBean(FinanzasPublicacionClient.class);
                    assertThat(context.getBean(InventarioClient.class))
                            .isSameAs(context.getBean(InventarioPublicacionClient.class))
                            .isInstanceOf(InventarioClientResiliente.class);
                });
    }

    @Test
    void fakeDisponibleParaPujasNoActivaPublicacion() {
        runner.withPropertyValues("app.inventario.modo=fake")
                .withUserConfiguration(FinanzasPublicacionClientHttp.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(InventarioClient.class)
                            .doesNotHaveBean(InventarioPublicacionClient.class)
                            .doesNotHaveBean(PublicarSubastaApplicationService.class);
                    assertThat(context.getBean(InventarioClient.class)).isInstanceOf(InventarioClientFake.class);
                });
    }

    @Test
    void sinFinanzasNoActivaPublicacion() {
        runner.withPropertyValues("app.inventario.modo=http")
                .run(context -> assertThat(context).hasSingleBean(InventarioPublicacionClient.class)
                        .doesNotHaveBean(PublicarSubastaApplicationService.class));
    }

    @Test
    void modoPredeterminadoMantieneFake() {
        runner.run(context -> assertThat(context).hasSingleBean(InventarioClient.class)
                .doesNotHaveBean(InventarioPublicacionClient.class)
                .doesNotHaveBean(PublicarSubastaApplicationService.class));
    }
}
