package com.nexusbattles.plataforma.correo.cola;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La configuracion de la cola llega por variables de entorno, y una variable
 * vacia en el .env llega como ausente o como cadena vacia: ninguna de las dos
 * puede dejar la cola sin intervalo, sin lote o sin reintentos.
 */
class ConfiguracionDeEntregaTest {

    @Test
    void sinConfiguracionSonLosValoresDelEncargo() {
        ConfiguracionDeEntrega porOmision = ConfiguracionDeEntrega.porOmision();

        assertThat(porOmision.activa()).isTrue();
        assertThat(porOmision.intervaloMs()).isEqualTo(2000L);
        assertThat(porOmision.lote()).isEqualTo(10);
        assertThat(porOmision.maxIntentos()).isEqualTo(8);
        assertThat(porOmision.esperas()).containsExactly(
                Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(5),
                Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(1));
        assertThat(porOmision.atascadoTras()).isEqualTo(Duration.ofMinutes(5));
        assertThat(porOmision.retencionDias()).isEqualTo(30);
        assertThat(porOmision.purgaIntervaloMs()).isEqualTo(3_600_000L);
    }

    @Test
    void losValoresAbsurdosSeCorrigenSolos() {
        ConfiguracionDeEntrega configuracion = new ConfiguracionDeEntrega(
                null, 0L, -3, 0, List.of(), Duration.ZERO, -1, -5L);

        assertThat(configuracion.intervaloMs()).isEqualTo(2000L);
        assertThat(configuracion.lote()).isEqualTo(10);
        assertThat(configuracion.maxIntentos()).isEqualTo(8);
        assertThat(configuracion.esperas()).isEqualTo(ConfiguracionDeEntrega.ESPERAS_POR_OMISION);
        assertThat(configuracion.atascadoTras()).isEqualTo(Duration.ofMinutes(5));
        assertThat(configuracion.retencionDias()).isEqualTo(30);
        assertThat(configuracion.purgaIntervaloMs()).isEqualTo(3_600_000L);
    }

    @Test
    void respetaLoQueSiEstaConfigurado() {
        ConfiguracionDeEntrega configuracion = new ConfiguracionDeEntrega(
                false, 500L, 3, 4, List.of(Duration.ofSeconds(1)), Duration.ofMinutes(1), 7, 60_000L);

        assertThat(configuracion.activa()).isFalse();
        assertThat(configuracion.intervaloMs()).isEqualTo(500L);
        assertThat(configuracion.lote()).isEqualTo(3);
        assertThat(configuracion.maxIntentos()).isEqualTo(4);
        assertThat(configuracion.esperas()).containsExactly(Duration.ofSeconds(1));
        assertThat(configuracion.atascadoTras()).isEqualTo(Duration.ofMinutes(1));
        assertThat(configuracion.retencionDias()).isEqualTo(7);
        assertThat(configuracion.purgaIntervaloMs()).isEqualTo(60_000L);
    }
}
