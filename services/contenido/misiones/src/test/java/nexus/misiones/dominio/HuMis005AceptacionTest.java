package nexus.misiones.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HuMis005AceptacionTest {

    @Test
    @DisplayName("acepta exploraciones entre 24 y 72 horas")
    void validaDuracionDeExploracion() {
        assertThatCode(() -> Misiones.exploracion("exploracion-minima", 24))
                .doesNotThrowAnyException();
        assertThatCode(() -> Misiones.exploracion("exploracion-maxima", 72))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> Misiones.exploracion("exploracion-corta", 23))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("24 a 72 horas");
        assertThatThrownBy(() -> Misiones.exploracion("exploracion-larga", 73))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("24 a 72 horas");
    }

    @Test
    @DisplayName("publica la cantidad de encuentros y las recompensas configuradas")
    void publicaDetalleDeExploracion() {
        Mision exploracion = Misiones.exploracion("expedicion", 48, Misiones.SOMBRA_DEL_OLVIDO);

        assertThat(exploracion.categoria()).isEqualTo(Categoria.EXPLORACION);
        assertThat(exploracion.encuentros()).isEqualTo(2);
        assertThat(exploracion.recompensas()).isNotNull();
        assertThat(exploracion.masters()).containsExactly(Misiones.SOMBRA_DEL_OLVIDO);
    }

    @Test
    @DisplayName("calcula el progreso de una exploración en curso")
    void calculaProgresoTemporal() {
        Instant inicio = Instant.parse("2026-10-07T00:00:00Z");
        Ejecucion ejecucion = Ejecucion.nueva(
                UUID.randomUUID(),
                "expedicion",
                "jugador",
                Misiones.HEROE,
                List.of(),
                Escalon.NORMAL,
                inicio,
                Duration.ofHours(48),
                1L,
                null);

        assertThat(ejecucion.progreso(inicio.plus(Duration.ofHours(12)))).isEqualTo(0.25);
        assertThat(ejecucion.terminaEn()).isEqualTo(inicio.plus(Duration.ofHours(48)));
    }
}
