package nexus.misiones.ia;

import static nexus.misiones.ia.EscenarioDeCombate.COSTOS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nexus.misiones.dominio.simulacion.DecididaPor;
import nexus.misiones.dominio.simulacion.DecisionDeTurno;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import nexus.misiones.dominio.simulacion.Simulacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La simulacion completa con el modelo entrenado de verdad ({@code ia/modelos/1.0.0/}), no con el sintetico de prueba:
 * lo que la imagen lleva a DEV, visto desde los eventos que quedan registrados. Criterios 1 y 2 de HU-SIM-008 con el
 * modelo real. La confianza minima es la que trae {@code application.properties} (0,6).
 */
class SimulacionConModeloVersionadoTest {

    private static final double CONFIANZA_POR_OMISION = 0.6;

    private static DecisorConModelo decisor() {
        return new DecisorConModelo(new ReglaDeRotaciones(COSTOS), PuntuadorOnnx.cargar(ModeloVersionado.onnx()),
                CONFIANZA_POR_OMISION);
    }

    @Test
    @DisplayName("criterio 1: decide el modelo, y el evento dice que fue el modelo 1.0.0 y entre que candidatas")
    void decideElModelo() {
        try (DecisorConModelo decisor = decisor()) {
            Simulacion simulacion = EscenarioDeCombate.simular(decisor);

            List<EventoDeCombate.Jugada> jugadas = simulacion.eventos().stream().map(EventoDeCombate::jugada)
                    .filter(j -> j != null).toList();
            List<EventoDeCombate.Jugada> delModelo = jugadas.stream()
                    .filter(j -> j.decididaPor() == DecididaPor.MODELO).toList();

            assertThat(jugadas).isNotEmpty();
            assertThat(delModelo).as("jugadas que decidio el modelo de %d", jugadas.size()).isNotEmpty();
            assertThat(delModelo).allSatisfy(j -> {
                assertThat(j.versionDelModelo()).isEqualTo("1.0.0");
                assertThat(j.candidatas()).hasSizeGreaterThanOrEqualTo(2);
                assertThat(j.candidatas()).allSatisfy(c -> assertThat(c.puntaje()).isNotNull());
                assertThat(j.candidatas()).extracting(DecisionDeTurno.Candidata::accion).contains(j.ejecutada());
            });
            // Toda jugada en la que el modelo opino (aunque la regla se haya quedado con la decision) lo dice con
            // su version y sus candidatas; la que no lo consulto no atribuye nada al modelo.
            assertThat(jugadas).allSatisfy(j -> {
                if (j.candidatas().isEmpty()) {
                    assertThat(j.decididaPor()).isEqualTo(DecididaPor.REGLA);
                    assertThat(j.versionDelModelo()).isNull();
                } else {
                    assertThat(j.versionDelModelo()).isEqualTo("1.0.0");
                }
            });
        }
    }

    @Test
    @DisplayName("criterio 2: nunca rompe la rotacion, el poder ni la recarga, y el motor no rechaza nada")
    void respetaRotacionPoderYRecarga() {
        try (DecisorConModelo decisor = decisor()) {
            Simulacion simulacion = EscenarioDeCombate.simular(decisor);

            Map<String, String> ultimaEspecialDeCadaUno = new HashMap<>();
            for (EventoDeCombate e : simulacion.eventos()) {
                if (e.jugada() == null) {
                    continue;
                }
                EventoDeCombate.Jugada j = e.jugada();
                assertThat(EscenarioDeCombate.accionesPermitidas(e)).as("secuencia %d", e.secuencia())
                        .contains(j.ejecutada());
                assertThat(j.enValorBase()).as("secuencia %d: jugada sin poder suficiente", e.secuencia()).isFalse();
                assertThat(j.costoDePoder()).isLessThanOrEqualTo(e.antes().actor().poder());
                assertThat(j.rechazadas()).as("el motor no rechazo nada").isEmpty();

                // Recarga: la misma habilidad especial no sale en dos turnos seguidos del mismo combatiente.
                String quien = e.encuentro() + "/" + e.actor().lado();
                if (!DecisionDeTurno.ATAQUE_BASICO.equals(j.ejecutada())) {
                    assertThat(ultimaEspecialDeCadaUno.put(quien, j.ejecutada()))
                            .as("secuencia %d repite %s sin recargar", e.secuencia(), j.ejecutada())
                            .isNotEqualTo(j.ejecutada());
                } else {
                    ultimaEspecialDeCadaUno.remove(quien);
                }
            }
        }
    }

    @Test
    @DisplayName("es reproducible: la misma semilla y el mismo modelo dan los mismos eventos")
    void reproducible() {
        List<List<EventoDeCombate>> corridas = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            try (DecisorConModelo decisor = decisor()) {
                corridas.add(EscenarioDeCombate.simular(decisor).eventos());
            }
        }

        assertThat(corridas.get(0)).isEqualTo(corridas.get(1));
    }
}
