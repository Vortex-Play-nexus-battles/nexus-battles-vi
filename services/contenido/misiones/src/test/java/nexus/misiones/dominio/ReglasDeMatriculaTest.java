package nexus.misiones.dominio;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que la mision exige antes de aceptar un heroe (7.8.6 y 7.8.11): que no
 * este bloqueada, que el jugador no la tenga ya en curso, el escalon
 * desbloqueado y, en los desafios, intentos en el periodo.
 */
class ReglasDeMatriculaTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");

    private static SituacionDelJugador situacion(EstadoMision estado, String motivo, Set<Escalon> escalones) {
        return new SituacionDelJugador(estado, motivo, null, null, false, escalones, false);
    }

    private static final SituacionDelJugador LIBRE =
            situacion(EstadoMision.DISPONIBLE, null, Set.of(Escalon.NORMAL));

    @Test
    @DisplayName("disponible, escalon normal: se acepta")
    void aceptada() {
        assertThatCode(() -> ReglasDeMatricula.exigir(Misiones.templo(), LIBRE, Escalon.NORMAL, null, 0, AHORA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bloqueada por la historia: 409 con el motivo")
    void bloqueada() {
        SituacionDelJugador bloqueada = situacion(EstadoMision.BLOQUEADA,
                "Completa «El Templo Olvidado» para desbloquearla.", Set.of(Escalon.NORMAL));

        assertThatThrownBy(() -> ReglasDeMatricula.exigir(Misiones.templo(), bloqueada, Escalon.NORMAL, null, 0,
                AHORA))
                .isInstanceOf(ReglaDeMisionIncumplida.class)
                .hasMessage("Completa «El Templo Olvidado» para desbloquearla.");
    }

    @Test
    @DisplayName("ya en curso para este jugador: no se abre otra a la vez")
    void yaEnCurso() {
        assertThatThrownBy(() -> ReglasDeMatricula.exigir(Misiones.templo(),
                situacion(EstadoMision.EN_PROGRESO, null, Set.of(Escalon.NORMAL)), Escalon.NORMAL, null, 0, AHORA))
                .isInstanceOf(ReglaDeMisionIncumplida.class)
                .hasMessageContaining("ya tienes esta misión en curso");
    }

    @Test
    @DisplayName("un escalon sin desbloquear se rechaza, y el Mitico sin cifra del PO tambien")
    void escalones() {
        assertThatThrownBy(() -> ReglasDeMatricula.exigir(Misiones.templo(), LIBRE, Escalon.HEROICO, null, 0,
                AHORA))
                .isInstanceOf(ReglaDeMisionIncumplida.class)
                .hasMessageContaining("Normal");

        SituacionDelJugador todo = situacion(EstadoMision.COMPLETADA, null,
                Set.of(Escalon.NORMAL, Escalon.HEROICO, Escalon.LEGENDARIO, Escalon.MITICO));
        assertThatThrownBy(() -> ReglasDeMatricula.exigir(Misiones.templo(), todo, Escalon.MITICO, null, 0, AHORA))
                .isInstanceOf(ReglaDeMisionIncumplida.class)
                .hasMessageContaining("Mítico");
        assertThatCode(() -> ReglasDeMatricula.exigir(Misiones.templo(), todo, Escalon.MITICO, 3.0, 0, AHORA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un desafio sin intentos en el periodo se rechaza hasta el siguiente")
    void intentos() {
        Mision desafio = Misiones.desafio("torre", new Intentos(2, Periodo.DIARIO));

        assertThatCode(() -> ReglasDeMatricula.exigir(desafio, LIBRE, Escalon.NORMAL, null, 1, AHORA))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ReglasDeMatricula.exigir(desafio, LIBRE, Escalon.NORMAL, null, 2, AHORA))
                .isInstanceOf(ReglaDeMisionIncumplida.class)
                .hasMessageContaining("2 intentos");
    }

    @Test
    @DisplayName("una mision de tiempo limitado vencida ya no admite heroes")
    void vencida() {
        Mision base = Misiones.historia("limitada", List.of());
        Mision limitada = new Mision(base.id(), base.origen(), base.nombre(), base.categoria(),
                base.descripcionBreve(), null, base.dificultad(), base.duracionHoras(), null, List.of(),
                base.narrativa(), null, base.objetivos(), base.enemigos(), base.jefe(), List.of(),
                base.recompensas(), false, AHORA.minusSeconds(1), null);

        assertThatThrownBy(() -> ReglasDeMatricula.exigir(limitada, LIBRE, Escalon.NORMAL, null, 0, AHORA))
                .isInstanceOf(ReglaDeMisionIncumplida.class);
    }

    @Test
    @DisplayName("los periodos de los desafios se cuentan en UTC; la semana empieza el lunes")
    void periodos() {
        org.assertj.core.api.Assertions.assertThat(Periodo.DIARIO.inicio(AHORA))
                .isEqualTo(Instant.parse("2026-09-25T00:00:00Z"));
        org.assertj.core.api.Assertions.assertThat(Periodo.SEMANAL.inicio(AHORA))
                .isEqualTo(Instant.parse("2026-09-21T00:00:00Z"));
    }
}
