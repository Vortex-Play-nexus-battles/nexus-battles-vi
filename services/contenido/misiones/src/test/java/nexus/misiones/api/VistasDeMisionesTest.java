package nexus.misiones.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.RecompensasDeEjecucion;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VistasDeMisionesTest {

    @Test
    @DisplayName("HU-MIS-005: la misión activa publica avance, tiempo restante y recompensas acumuladas")
    void publicaProgresoDeExploracion() {
        Instant inicio = Instant.parse("2026-10-14T00:00:00Z");
        Instant consulta = inicio.plus(Duration.ofHours(9));
        Mision mision = Misiones.exploracion("expedicion", 24);
        Ejecucion ejecucion = Ejecucion.nueva(UUID.randomUUID(), mision.id(), "jugador", Misiones.HEROE,
                List.of(), Escalon.NORMAL, inicio, Duration.ofHours(24), 7L, null);
        ejecucion.prepararExploracion(3);
        RecompensasDeEjecucion acumuladas = new RecompensasDeEjecucion(0,
                List.of(new RecompensasDeEjecucion.ObjetoGanado("Fragmento", "producto-1", 2)), List.of(),
                18.5, List.of(), List.of(), false);
        ejecucion.registrarProgresoDeExploracion(new ResultadoDeMision(true, false, 1, true,
                10, 2, 4, 0, List.of(), List.of(), List.of(), 80, 18.5, List.of(3)), acumuladas);

        Respuestas.MisionActiva vista = new VistasDeMisiones(null, null,
                Clock.fixed(consulta, ZoneOffset.UTC)).activa(ejecucion, mision, "Pierdes lo acumulado");

        assertThat(vista.encuentrosCompletados()).isEqualTo(1);
        assertThat(vista.encuentrosTotales()).isEqualTo(3);
        assertThat(vista.tiempoRestanteSegundos()).isEqualTo(Duration.ofHours(15).toSeconds());
        assertThat(vista.recompensasAcumuladas().experiencia()).isEqualTo(18.5);
        assertThat(vista.recompensasAcumuladas().objetos())
                .containsExactly(new Respuestas.ObjetoAcumulado("Fragmento", 2));
    }
}
