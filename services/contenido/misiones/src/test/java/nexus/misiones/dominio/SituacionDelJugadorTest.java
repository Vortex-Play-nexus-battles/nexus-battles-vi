package nexus.misiones.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El estado de una mision PARA UN JUGADOR (7.8.7 y HU-MIS-010): Bloqueada si
 * le falta la anterior de la historia, En progreso si tiene una ejecucion en
 * curso, el resultado de la ultima si ya la jugo, y si no, Disponible.
 */
class SituacionDelJugadorTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final Map<String, String> NOMBRES = Map.of("templo-olvidado", "El Templo Olvidado",
            "prologo", "Prólogo");

    private static Ejecucion ejecucion(String mision, Escalon escalon, Instant inicio) {
        return Ejecucion.nueva(UUID.randomUUID(), mision, "uid-1", EjecucionTest.HEROE, List.of(), escalon,
                inicio, Duration.ofHours(1), 1L, null);
    }

    private static Ejecucion terminada(String mision, Escalon escalon, boolean exito, Instant inicio) {
        Ejecucion e = ejecucion(mision, escalon, inicio);
        e.terminar(new ResultadoDeMision(exito, exito, 1, exito, 1, 1, 1, 0, List.of(), List.of(), List.of(),
                        50, 1, List.of()),
                new RecompensasDeEjecucion(0, List.of(), List.of(), 1, List.of(), List.of(), false),
                false, inicio.plus(Duration.ofHours(1)));
        return e;
    }

    @Test
    @DisplayName("sin ejecuciones ni requisitos, disponible y nueva")
    void disponible() {
        SituacionDelJugador s = SituacionDelJugador.calcular(Misiones.templo(), List.of(), Set.of(),
                NOMBRES::get, AHORA);

        assertThat(s.estado()).isEqualTo(EstadoMision.DISPONIBLE);
        assertThat(s.nueva()).isTrue();
        assertThat(s.escalonesDesbloqueados()).containsExactly(Escalon.NORMAL);
        assertThat(s.ultimaEjecucionId()).isNull();
    }

    @Test
    @DisplayName("una mision de historia sin la anterior completada esta bloqueada y dice cual falta")
    void bloqueada() {
        SituacionDelJugador s = SituacionDelJugador.calcular(Misiones.historiaTrasDe("segunda", "templo-olvidado"),
                List.of(), Set.of(), NOMBRES::get, AHORA);

        assertThat(s.estado()).isEqualTo(EstadoMision.BLOQUEADA);
        assertThat(s.motivoBloqueo()).isEqualTo("Completa «El Templo Olvidado» para desbloquearla.");
    }

    @Test
    @DisplayName("completada la anterior, se desbloquea")
    void desbloqueada() {
        SituacionDelJugador s = SituacionDelJugador.calcular(Misiones.historiaTrasDe("segunda", "templo-olvidado"),
                List.of(), Set.of("templo-olvidado"), NOMBRES::get, AHORA);

        assertThat(s.estado()).isEqualTo(EstadoMision.DISPONIBLE);
    }

    @Test
    @DisplayName("con una ejecucion en curso esta En progreso, con su progreso")
    void enProgreso() {
        Ejecucion enCurso = ejecucion("templo-olvidado", Escalon.NORMAL, AHORA.minus(Duration.ofMinutes(15)));

        SituacionDelJugador s = SituacionDelJugador.calcular(Misiones.templo(), List.of(enCurso), Set.of(),
                NOMBRES::get, AHORA);

        assertThat(s.estado()).isEqualTo(EstadoMision.EN_PROGRESO);
        assertThat(s.progreso()).isEqualTo(0.25);
        assertThat(s.nueva()).isFalse();
    }

    @Test
    @DisplayName("si no esta en curso, manda el resultado de la ultima ejecucion y su reporte")
    void ultimoResultado() {
        Ejecucion vieja = terminada("templo-olvidado", Escalon.NORMAL, true, AHORA.minus(Duration.ofDays(2)));
        Ejecucion reciente = terminada("templo-olvidado", Escalon.NORMAL, false, AHORA.minus(Duration.ofDays(1)));
        Ejecucion abandonada = ejecucion("templo-olvidado", Escalon.NORMAL, AHORA.minus(Duration.ofHours(3)));
        abandonada.cancelar(AHORA.minus(Duration.ofHours(2)));

        SituacionDelJugador s = SituacionDelJugador.calcular(Misiones.templo(),
                List.of(vieja, reciente, abandonada), Set.of("templo-olvidado"), NOMBRES::get, AHORA);

        // La ultima fue abandonada; el ultimo REPORTE es el de la fallida.
        assertThat(s.estado()).isEqualTo(EstadoMision.ABANDONADA);
        assertThat(s.ultimaEjecucionId()).isEqualTo(reciente.id());
        assertThat(s.completadaAlgunaVez()).isTrue();
    }

    @Test
    @DisplayName("cada escalon se desbloquea completando el anterior (7.8.11), y no se pierde al fallar")
    void escalones() {
        Ejecucion normal = terminada("templo-olvidado", Escalon.NORMAL, true, AHORA.minus(Duration.ofDays(3)));
        Ejecucion heroicoFallido = terminada("templo-olvidado", Escalon.HEROICO, false,
                AHORA.minus(Duration.ofDays(2)));

        SituacionDelJugador s = SituacionDelJugador.calcular(Misiones.templo(), List.of(normal, heroicoFallido),
                Set.of("templo-olvidado"), NOMBRES::get, AHORA);

        assertThat(s.escalonesDesbloqueados()).containsExactly(Escalon.NORMAL, Escalon.HEROICO);
        assertThat(s.escalonMasAlto()).isEqualTo(Escalon.HEROICO);
    }
}
