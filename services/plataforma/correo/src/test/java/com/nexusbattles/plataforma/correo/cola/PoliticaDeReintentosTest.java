package com.nexusbattles.plataforma.correo.cola;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cuanto espera un correo entre intentos y cuando se deja de intentar. */
class PoliticaDeReintentosTest {

    private final PoliticaDeReintentos politica =
            new PoliticaDeReintentos(ConfiguracionDeEntrega.ESPERAS_POR_OMISION, 8);

    @ParameterizedTest(name = "tras el intento {0} se espera {1}")
    @CsvSource({
            "1, PT30S",
            "2, PT1M",
            "3, PT2M",
            "4, PT5M",
            "5, PT15M",
            "6, PT30M",
            "7, PT1H",
            // Pasado el ultimo valor se repite el ultimo.
            "8, PT1H",
            "20, PT1H",
    })
    void laEsperaCreceSegunElEncargo(int intentos, String espera) {
        assertThat(politica.esperaTras(intentos)).isEqualTo(Duration.parse(espera));
    }

    @Test
    void sinIntentosTodaviaLaEsperaEsLaPrimera() {
        assertThat(politica.esperaTras(0)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void seAgotaAlLlegarAlMaximoDeIntentos() {
        assertThat(politica.agotado(7)).isFalse();
        assertThat(politica.agotado(8)).isTrue();
        assertThat(politica.agotado(9)).isTrue();
        assertThat(politica.maxIntentos()).isEqualTo(8);
    }

    @Test
    void unaConfiguracionImposibleNoArranca() {
        assertThatThrownBy(() -> new PoliticaDeReintentos(List.of(), 8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeReintentos(null, 8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeReintentos(List.of(Duration.ZERO), 8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeReintentos(List.of(Duration.ofSeconds(-1)), 8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeReintentos(Arrays.asList(Duration.ofSeconds(1), null), 8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeReintentos(List.of(Duration.ofSeconds(1)), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
