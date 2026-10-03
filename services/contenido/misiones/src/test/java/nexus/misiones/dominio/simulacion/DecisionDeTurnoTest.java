package nexus.misiones.dominio.simulacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La decision lleva, ademas de la jugada, quien la tomo (regla o modelo) y lo que se considero (HU-SIM-008). */
class DecisionDeTurnoTest {

    @Test
    @DisplayName("una decision de la regla de siempre: sin rotacion conocida, sin modelo y sin candidatas")
    void decisionDeLaRegla() {
        DecisionDeTurno d = new DecisionDeTurno("Embate sangriento", 4, List.of(1, 0));

        assertThat(d.decididaPor()).isEqualTo(DecididaPor.REGLA);
        assertThat(d.rotacion()).isNull();
        assertThat(d.versionDelModelo()).isNull();
        assertThat(d.candidatas()).isEmpty();
        assertThat(d.esAtaqueBasico()).isFalse();
    }

    @Test
    @DisplayName("con la rotacion de la que salio, el resto queda igual")
    void conRotacion() {
        DecisionDeTurno d = new DecisionDeTurno("Embate sangriento", 4, List.of(1, 0)).deLaRotacion(2);

        assertThat(d.rotacion()).isEqualTo(2);
        assertThat(d.accion()).isEqualTo("Embate sangriento");
        assertThat(d.cursoresSiguientes()).containsExactly(1, 0);
    }

    @Test
    @DisplayName("considerando al modelo: si lo que elige es el modelo queda dicho, con la version y las candidatas")
    void deModelo() {
        List<DecisionDeTurno.Candidata> candidatas = List.of(
                new DecisionDeTurno.Candidata("Embate sangriento", 4, 1, 0.2),
                new DecisionDeTurno.Candidata("Ataque básico", 0, null, 0.8));

        DecisionDeTurno d = new DecisionDeTurno("Ataque básico", 0, List.of(0)).consultandoAlModelo(
                DecididaPor.MODELO, "v1-prueba", candidatas);

        assertThat(d.decididaPor()).isEqualTo(DecididaPor.MODELO);
        assertThat(d.versionDelModelo()).isEqualTo("v1-prueba");
        assertThat(d.candidatas()).isEqualTo(candidatas);
    }

    @Test
    @DisplayName("si el modelo se consulto pero decidio la regla, tambien queda la version y lo que puntuo")
    void consultadoPeroDecidioLaRegla() {
        DecisionDeTurno d = new DecisionDeTurno("Embate sangriento", 4, List.of(1)).consultandoAlModelo(
                DecididaPor.REGLA, "v1-prueba", List.of(new DecisionDeTurno.Candidata("Embate sangriento", 4, 1, 0.5)));

        assertThat(d.decididaPor()).isEqualTo(DecididaPor.REGLA);
        assertThat(d.versionDelModelo()).isEqualTo("v1-prueba");
        assertThat(d.candidatas()).hasSize(1);
    }

    @Test
    @DisplayName("sigue sin admitir una decision sin accion ni un costo negativo")
    void sigueValidando() {
        assertThatThrownBy(() -> new DecisionDeTurno(" ", 0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DecisionDeTurno("Embate sangriento", -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("una candidata no puede tener costo negativo ni nombre vacio")
    void candidataValida() {
        assertThatThrownBy(() -> new DecisionDeTurno.Candidata("", 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DecisionDeTurno.Candidata("Embate sangriento", -2, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
