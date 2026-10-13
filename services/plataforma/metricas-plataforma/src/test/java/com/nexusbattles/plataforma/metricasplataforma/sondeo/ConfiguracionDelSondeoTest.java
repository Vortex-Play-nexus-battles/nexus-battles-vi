package com.nexusbattles.plataforma.metricasplataforma.sondeo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RFINAL-08 — los plazos del sondeo interactivo salen de application.yml y se
 * pueden cambiar por variable de entorno sin recompilar (regla 10).
 */
@DisplayName("Configuracion del sondeo interactivo (RFINAL-08)")
class ConfiguracionDelSondeoTest {

    /** Lo que de verdad lee el servicio al arrancar, con los placeholders resueltos. */
    private static ConfiguracionDelSondeo laDeApplicationYml() throws IOException {
        StandardEnvironment entorno = new StandardEnvironment();
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("application.yml", new FileSystemResource("src/main/resources/application.yml"));
        yaml.forEach(fuente -> entorno.getPropertySources().addLast(fuente));
        return Binder.get(entorno).bind("sondeo", ConfiguracionDelSondeo.class).get();
    }

    @Test
    @DisplayName("application.yml declara los cuatro valores y enlazan: plazos acotados, ejecutor acotado, vigencia corta")
    void applicationYmlEnlaza() throws IOException {
        ConfiguracionDelSondeo sondeo = laDeApplicationYml();

        assertThat(sondeo.plazoDeConexion()).isEqualTo(Duration.ofMillis(1000));
        assertThat(sondeo.plazoDeRespuesta()).isEqualTo(Duration.ofMillis(2000));
        assertThat(sondeo.hilos()).isEqualTo(16);
        assertThat(sondeo.vigencia()).isEqualTo(Duration.ofSeconds(15));
        // Lo mas que puede tardar la pantalla, con un servicio colgado.
        assertThat(sondeo.plazoDeLaRonda()).isEqualTo(Duration.ofMillis(3500));
    }

    @Test
    @DisplayName("la ronda entera espera conectar + responder + un margen, y nada mas")
    void elPlazoDeLaRondaEsConexionMasRespuestaMasMargen() {
        ConfiguracionDelSondeo sondeo = new ConfiguracionDelSondeo(1000, 1500, 16, 15000);

        assertThat(sondeo.plazoDeLaRonda()).isEqualTo(Duration.ofMillis(3000));
    }

    @Test
    @DisplayName("un plazo o un tamano sin sentido no arranca: mejor un error claro que esperar para siempre")
    void valoresSinSentidoSeRechazan() {
        assertThatThrownBy(() -> new ConfiguracionDelSondeo(0, 1500, 16, 15000))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sondeo.conexion-ms");
        assertThatThrownBy(() -> new ConfiguracionDelSondeo(1000, -1, 16, 15000))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sondeo.respuesta-ms");
        assertThatThrownBy(() -> new ConfiguracionDelSondeo(1000, 1500, 0, 15000))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sondeo.hilos");
        assertThatThrownBy(() -> new ConfiguracionDelSondeo(1000, 1500, 16, -1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sondeo.vigencia-ms");
    }

    @Test
    @DisplayName("vigencia cero es valida: apaga la cache")
    void vigenciaCeroEsValida() {
        assertThat(new ConfiguracionDelSondeo(1000, 1500, 16, 0).vigencia()).isEqualTo(Duration.ZERO);
    }
}
