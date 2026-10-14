package nexus.misiones.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nexus.misiones.dominio.simulacion.TiradaDeMasters;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
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

    @Test
    @DisplayName("una exploración aplica una tirada de Máster por cada 24 horas")
    void calculaAparicionAcumuladaDeMaster() {
        Mision exploracion = Misiones.exploracion("expedicion", 48, Misiones.SOMBRA_DEL_OLVIDO);

        assertThat(TiradaDeMasters.tiradas(exploracion)).isEqualTo(2);
        assertThat(TiradaDeMasters.probabilidadDeAlguno(exploracion)).isCloseTo(0.2775, within(0.000001));
    }

    @Test
    @DisplayName("distribuye los encuentros en intervalos iguales y conserva el acumulado ante un fallo")
    void registraProgresoAcumulado() {
        Instant inicio = Instant.parse("2026-10-07T00:00:00Z");
        Ejecucion ejecucion = Ejecucion.nueva(UUID.randomUUID(), "expedicion", "jugador", Misiones.HEROE,
                List.of(), Escalon.NORMAL, inicio, Duration.ofHours(24), 1L, null);
        ejecucion.prepararExploracion(3);

        assertThat(ejecucion.siguienteEncuentroEn()).isEqualTo(inicio.plus(Duration.ofHours(8)));
        assertThat(ejecucion.listaParaSimular(inicio.plus(Duration.ofHours(8)).minusMillis(1))).isFalse();
        assertThat(ejecucion.listaParaSimular(inicio.plus(Duration.ofHours(8)))).isTrue();

        RecompensasDeEjecucion acumuladas = new RecompensasDeEjecucion(0, List.of(), List.of(), 12.5,
                List.of(), List.of(), false);
        ejecucion.registrarProgresoDeExploracion(resultadoParcial(1), acumuladas);

        assertThat(ejecucion.encuentrosCompletados()).isEqualTo(1);
        assertThat(ejecucion.recompensasAcumuladas().experiencia()).isEqualTo(12.5);
        assertThat(ejecucion.siguienteEncuentroEn()).isEqualTo(inicio.plus(Duration.ofHours(16)));
    }

    @Test
    @DisplayName("al cancelar pierde el acumulado salvo cuando la simulacion venia fallando")
    void aplicaReglaDeCancelacion() {
        Instant inicio = Instant.parse("2026-10-07T00:00:00Z");
        RecompensasDeEjecucion acumuladas = new RecompensasDeEjecucion(0, List.of(), List.of(), 9.5,
                List.of(), List.of(), false);
        Ejecucion abandono = exploracionEnProgreso(inicio, acumuladas);
        abandono.cancelar(inicio.plus(Duration.ofHours(9)));

        assertThat(abandono.canceladaSinPenalizacion()).isFalse();
        assertThat(abandono.recompensasAcumuladas().experiencia()).isZero();

        Ejecucion falloDelSistema = exploracionEnProgreso(inicio, acumuladas);
        falloDelSistema.simulacionAplazada(inicio.plus(Duration.ofHours(9)), Duration.ofSeconds(30), "motor caido");
        falloDelSistema.cancelar(inicio.plus(Duration.ofHours(9)).plusSeconds(1));

        assertThat(falloDelSistema.canceladaSinPenalizacion()).isTrue();
        assertThat(falloDelSistema.recompensas()).isEqualTo(acumuladas);
    }

    private static Ejecucion exploracionEnProgreso(Instant inicio, RecompensasDeEjecucion acumuladas) {
        Ejecucion ejecucion = Ejecucion.nueva(UUID.randomUUID(), "expedicion", "jugador", Misiones.HEROE,
                List.of(), Escalon.NORMAL, inicio, Duration.ofHours(24), 1L, null);
        ejecucion.prepararExploracion(3);
        ejecucion.registrarProgresoDeExploracion(resultadoParcial(1), acumuladas);
        return ejecucion;
    }

    private static ResultadoDeMision resultadoParcial(int encuentros) {
        return new ResultadoDeMision(true, false, encuentros, true, 10, 2, 3, 0,
                List.of(), List.of(), List.of(), 80, 12.5, List.of(4));
    }
}
