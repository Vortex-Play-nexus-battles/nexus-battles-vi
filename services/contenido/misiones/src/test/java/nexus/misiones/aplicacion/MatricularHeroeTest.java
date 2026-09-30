package nexus.misiones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.EstrategiaGuardada;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.Intentos;
import nexus.misiones.dominio.MisionNoEncontrada;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.ParametrosDeRecompensa;
import nexus.misiones.dominio.Periodo;
import nexus.misiones.dominio.ReglaDeMisionIncumplida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Matricula (7.8.6, HU-MIS-008 y HU-MIS-009): la mision disponible, el heroe
 * del jugador, libre y equipado, la estrategia validada por heroes, el heroe
 * bloqueado en el inventario y la ejecucion guardada; y si algo falla despues
 * de bloquear, el heroe se libera (HU-MIS-009: «no queda reservado»).
 */
class MatricularHeroeTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T10:00:00Z");
    private static final String JUGADOR = "11111111-1111-4111-8111-111111111111";
    private static final String OTRO = "22222222-2222-4222-8222-222222222222";

    private Dobles.Ejecuciones ejecuciones;
    private Dobles.Estrategias estrategias;
    private Dobles.Inventario inventario;
    private Dobles.Productos productos;
    private Dobles.Heroes heroes;
    private MatricularHeroe matricular;

    @BeforeEach
    void preparar() {
        ejecuciones = new Dobles.Ejecuciones();
        estrategias = new Dobles.Estrategias();
        inventario = new Dobles.Inventario().conHeroe("h-1", JUGADOR, "p-armas", true);
        productos = new Dobles.Productos();
        productos.prototipos.put("p-armas", "Guerrero Armas");
        productos.prototipos.put("p-chaman", "Chamán");
        heroes = new Dobles.Heroes();
        matricular = conParametros(ParametrosDeMisiones.porOmision());
    }

    private MatricularHeroe conParametros(ParametrosDeMisiones parametros) {
        Dobles.Catalogo catalogo = new Dobles.Catalogo(List.of(Misiones.templo(),
                Misiones.historiaTrasDe("segunda", "templo-olvidado"),
                Misiones.desafio("torre-de-prueba", new Intentos(1, Periodo.DIARIO))), List.of());
        return new MatricularHeroe(catalogo, ejecuciones, estrategias, inventario, productos, heroes, parametros,
                Clock.fixed(AHORA, ZoneOffset.UTC), () -> 77L);
    }

    private static SolicitudDeMatricula solicitud(String mision, String heroe) {
        return new SolicitudDeMatricula(mision, heroe, List.of(List.of("Ataque básico")), null, null);
    }

    @Test
    @DisplayName("envia al heroe: bloqueado en el inventario, ejecucion en progreso y estrategia guardada")
    void matricula() {
        Matricula matricula = matricular.matricular(JUGADOR, solicitud("templo-olvidado", "h-1"));

        Ejecucion ejecucion = matricula.ejecucion();
        assertThat(matricula.repetida()).isFalse();
        assertThat(ejecucion.estado()).isEqualTo(EstadoEjecucion.EN_PROGRESO);
        assertThat(ejecucion.terminaEn()).isEqualTo(AHORA.plus(Duration.ofHours(12)));
        assertThat(ejecucion.heroe().prototipo()).isEqualTo("Guerrero Armas");
        assertThat(ejecucion.heroe().vida()).isEqualTo(44);
        assertThat(ejecucion.semilla()).isEqualTo(77L);
        assertThat(inventario.bloqueados).containsEntry("h-1", ejecucion.id());
        assertThat(ejecuciones.buscar(ejecucion.id())).isPresent();
        assertThat(estrategias.buscar(JUGADOR, "h-1")).map(EstrategiaGuardada::rotaciones)
                .contains(List.of(List.of("Ataque básico")));
    }

    @Test
    @DisplayName("una hora de mision dura lo que diga el parametro (banco E2E: segundos)")
    void relojAcelerado() {
        ParametrosDeMisiones rapidos = new ParametrosDeMisiones(Duration.ofSeconds(1), Duration.ofSeconds(1), 5,
                false, null, null, ParametrosDeRecompensa.provisionales());

        Ejecucion ejecucion = conParametros(rapidos).matricular(JUGADOR, solicitud("templo-olvidado", "h-1"))
                .ejecucion();

        assertThat(ejecucion.terminaEn()).isEqualTo(AHORA.plusSeconds(12));
    }

    @Test
    @DisplayName("sin rotaciones usa la estrategia guardada del heroe")
    void estrategiaGuardada() {
        estrategias.guardar(new EstrategiaGuardada(JUGADOR, "h-1", "Guerrero Armas", 1,
                List.of(List.of("Embate sangriento")), AHORA));

        matricular.matricular(JUGADOR, new SolicitudDeMatricula("templo-olvidado", "h-1", null, null, null));

        assertThat(heroes.validaciones).containsExactly("Guerrero Armas@1 [[Embate sangriento]]");
    }

    @Test
    @DisplayName("la misma clave de idempotencia devuelve la misma ejecucion sin bloquear otra vez")
    void idempotente() {
        SolicitudDeMatricula conClave = new SolicitudDeMatricula("templo-olvidado", "h-1", List.of(), null, "clave-1");

        Matricula primera = matricular.matricular(JUGADOR, conClave);
        Matricula segunda = matricular.matricular(JUGADOR, conClave);

        assertThat(segunda.repetida()).isTrue();
        assertThat(segunda.ejecucion().id()).isEqualTo(primera.ejecucion().id());
        assertThat(inventario.llamadas.stream().filter(l -> l.startsWith("bloquear"))).hasSize(1);
    }

    @Test
    @DisplayName("una mision que no existe: 404")
    void misionInexistente() {
        assertThatThrownBy(() -> matricular.matricular(JUGADOR, solicitud("no-existe", "h-1")))
                .isInstanceOf(MisionNoEncontrada.class);
    }

    @Test
    @DisplayName("un heroe de otro jugador responde como si no existiera y no se bloquea")
    void heroeAjeno() {
        inventario.conHeroe("h-2", OTRO, "p-armas", true);

        assertThatThrownBy(() -> matricular.matricular(JUGADOR, solicitud("templo-olvidado", "h-2")))
                .isInstanceOf(HeroeNoEncontrado.class);
        assertThat(inventario.bloqueados).isEmpty();
    }

    @Test
    @DisplayName("un heroe ya en otra mision: 409 «ya esta ocupado en una mision»")
    void heroeOcupado() {
        matricular.matricular(JUGADOR, solicitud("templo-olvidado", "h-1"));

        assertThatThrownBy(() -> matricular.matricular(JUGADOR, solicitud("torre-de-prueba", "h-1")))
                .isInstanceOf(HeroeOcupado.class)
                .hasMessageContaining("ocupado en una misión");
    }

    @Test
    @DisplayName("sin equipo y sanador en solitario: 422 con los dos motivos a la vez")
    void noApto() {
        inventario.conHeroe("h-3", JUGADOR, "p-chaman", false);

        assertThatThrownBy(() -> matricular.matricular(JUGADOR, solicitud("templo-olvidado", "h-3")))
                .isInstanceOfSatisfying(HeroeNoApto.class, e -> assertThat(e.motivos()).hasSize(2)
                        .anySatisfy(m -> assertThat(m).contains("completar su mazo"))
                        .anySatisfy(m -> assertThat(m).contains("sanador")));
        assertThat(inventario.bloqueados).isEmpty();
    }

    @Test
    @DisplayName("una estrategia que heroes rechaza: 422 con su motivo, y el heroe queda libre")
    void estrategiaRechazada() {
        heroes.motivoDeRechazo = "La rotación 1 usa una habilidad que Guerrero Armas no posee en nivel 1.";

        assertThatThrownBy(() -> matricular.matricular(JUGADOR, solicitud("templo-olvidado", "h-1")))
                .isInstanceOf(EstrategiaInvalida.class)
                .hasMessage(heroes.motivoDeRechazo);
        assertThat(inventario.bloqueados).isEmpty();
    }

    @Test
    @DisplayName("una mision bloqueada por la historia: 409 con el motivo")
    void bloqueada() {
        assertThatThrownBy(() -> matricular.matricular(JUGADOR, solicitud("segunda", "h-1")))
                .isInstanceOf(ReglaDeMisionIncumplida.class)
                .hasMessage("Completa «El Templo Olvidado» para desbloquearla.");
    }

    @Test
    @DisplayName("si no se puede guardar la ejecucion, el heroe se libera y el error sube")
    void compensacion() {
        ejecuciones.fallarAlGuardar = new IllegalStateException("mongo caido");

        assertThatThrownBy(() -> matricular.matricular(JUGADOR, solicitud("templo-olvidado", "h-1")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(inventario.bloqueados).isEmpty();
        assertThat(inventario.llamadas).anySatisfy(l -> assertThat(l).startsWith("liberar h-1 0.0"));
    }

    @Test
    @DisplayName("el inventario caido al bloquear: 503 y nada queda a medias")
    void inventarioCaido() {
        inventario.fallarAlBloquear = Dobles.caido("inventario");

        assertThatThrownBy(() -> matricular.matricular(JUGADOR, solicitud("templo-olvidado", "h-1")))
                .isInstanceOf(DependenciaDegradada.class);
        assertThat(ejecuciones.todas()).isEmpty();
    }

    @Test
    @DisplayName("un desafio sin intentos en el periodo: 409")
    void sinIntentos() {
        inventario.conHeroe("h-4", JUGADOR, "p-armas", true);
        Ejecucion primera = matricular.matricular(JUGADOR, solicitud("torre-de-prueba", "h-1")).ejecucion();
        primera.cancelar(AHORA);
        ejecuciones.guardar(primera);

        assertThatThrownBy(() -> matricular.matricular(JUGADOR, solicitud("torre-de-prueba", "h-4")))
                .isInstanceOf(ReglaDeMisionIncumplida.class)
                .hasMessageContaining("intentos");
    }

    @Test
    @DisplayName("un escalon sin desbloquear: 409 antes de tocar el inventario")
    void escalonBloqueado() {
        SolicitudDeMatricula heroico = new SolicitudDeMatricula("templo-olvidado", "h-1", List.of(), Escalon.HEROICO,
                null);

        assertThatThrownBy(() -> matricular.matricular(JUGADOR, heroico))
                .isInstanceOf(ReglaDeMisionIncumplida.class);
        assertThat(inventario.llamadas).noneMatch(l -> l.startsWith("bloquear"));
    }
}
