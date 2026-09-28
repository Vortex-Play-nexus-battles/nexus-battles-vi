package nexus.misiones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import nexus.misiones.dominio.Categoria;
import nexus.misiones.dominio.Dificultad;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EjecucionNoEncontrada;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.EstadoMision;
import nexus.misiones.dominio.EstrategiaGuardada;
import nexus.misiones.dominio.Mision;
import nexus.misiones.dominio.MisionNoEncontrada;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.RecompensasDeEjecucion;
import nexus.misiones.dominio.SinReporteTodavia;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tablon, detalle, en curso, reporte, historial, favoritas y estrategias (7.8.8 y 7.8.9). */
class ConsultasTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final String JUGADOR = "11111111-1111-4111-8111-111111111111";
    private static final String OTRO = "22222222-2222-4222-8222-222222222222";

    private Dobles.Ejecuciones ejecuciones;
    private Dobles.Favoritas favoritas;
    private Dobles.Estrategias estrategias;
    private Dobles.Inventario inventario;
    private Dobles.Productos productos;
    private Dobles.Heroes heroes;
    private Dobles.Catalogo catalogo;
    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);

    @BeforeEach
    void preparar() {
        ejecuciones = new Dobles.Ejecuciones();
        favoritas = new Dobles.Favoritas();
        estrategias = new Dobles.Estrategias();
        inventario = new Dobles.Inventario().conHeroe("h-1", JUGADOR, "p-armas", true);
        productos = new Dobles.Productos();
        productos.prototipos.put("p-armas", "Guerrero Armas");
        heroes = new Dobles.Heroes();
        List<Mision> misiones = new ArrayList<>();
        misiones.add(Misiones.templo());
        misiones.add(Misiones.historiaTrasDe("segunda", "templo-olvidado"));
        for (int i = 0; i < 18; i++) {
            misiones.add(Misiones.exploracion("exploracion-" + i, 24 + i));
        }
        catalogo = new Dobles.Catalogo(misiones, List.of());
    }

    private ConsultarMisiones consultar() {
        return new ConsultarMisiones(catalogo, ejecuciones, favoritas, reloj);
    }

    private ConsultarEjecuciones ejecucionesDe() {
        return new ConsultarEjecuciones(catalogo, ejecuciones);
    }

    private Ejecucion terminada(String mision, boolean exito, Instant inicio, List<RecompensasDeEjecucion.EpicaGanada> epicas) {
        Ejecucion e = Ejecucion.nueva(UUID.randomUUID(), mision, JUGADOR, Misiones.HEROE, List.of(),
                Escalon.NORMAL, inicio, Duration.ofHours(12), 1L, null);
        e.terminar(new ResultadoDeMision(exito, exito, 3, exito, 10, 5, 7, 1, List.of(), List.of(), List.of(), 70,
                        30, List.of(3)),
                new RecompensasDeEjecucion(exito ? 50 : 0, List.of(), epicas, 30, List.of(), List.of(), exito),
                false, inicio.plus(Duration.ofHours(12)));
        return ejecuciones.guardar(e);
    }

    @Test
    @DisplayName("el tablon filtra por categoria y pagina de dieciseis en dieciseis")
    void tablonPaginado() {
        PaginaDeMisiones primera = consultar().tablero(JUGADOR, Categoria.EXPLORACION, null, null, null, 0);
        PaginaDeMisiones segunda = consultar().tablero(JUGADOR, Categoria.EXPLORACION, null, null, null, 1);

        assertThat(primera.misiones()).hasSize(16);
        assertThat(primera.total()).isEqualTo(18);
        assertThat(primera.totalPaginas()).isEqualTo(2);
        assertThat(segunda.misiones()).hasSize(2);
        assertThat(consultar().tablero(JUGADOR, Categoria.HISTORIA, null, null, null, 0).misiones())
                .extracting(m -> m.mision().id()).containsExactly("templo-olvidado", "segunda");
    }

    @Test
    @DisplayName("los filtros se combinan con Y: dificultad, estado y duracion (HU-MIS-002)")
    void filtros() {
        assertThat(consultar().tablero(JUGADOR, Categoria.HISTORIA, Dificultad.NORMAL, null, null, 0).misiones())
                .extracting(m -> m.mision().id()).containsExactly("templo-olvidado");
        assertThat(consultar().tablero(JUGADOR, Categoria.HISTORIA, null, EstadoMision.BLOQUEADA, null, 0).misiones())
                .extracting(m -> m.mision().id()).containsExactly("segunda");
        // «segunda» dura una hora pero esta bloqueada: la condicion de estado la deja fuera.
        assertThat(consultar().tablero(JUGADOR, Categoria.HISTORIA, null, EstadoMision.DISPONIBLE, "HASTA_12", 0)
                .misiones()).extracting(m -> m.mision().id()).containsExactly("templo-olvidado");
        assertThat(consultar().tablero(JUGADOR, Categoria.EXPLORACION, null, null, "DE_12_A_24", 0).misiones())
                .extracting(m -> m.mision().id()).containsExactly("exploracion-0");
    }

    @Test
    @DisplayName("el estado de la tarjeta es el del jugador que pregunta, con su favorita")
    void estadoDelJugador() {
        terminada("templo-olvidado", true, AHORA.minus(Duration.ofDays(1)), List.of());
        favoritas.marcar(JUGADOR, "segunda", AHORA);

        List<MisionParaJugador> mias = consultar().tablero(JUGADOR, Categoria.HISTORIA, null, null, null, 0).misiones();
        List<MisionParaJugador> delOtro = consultar().tablero(OTRO, Categoria.HISTORIA, null, null, null, 0).misiones();

        assertThat(mias).extracting(m -> m.situacion().estado())
                .containsExactly(EstadoMision.COMPLETADA, EstadoMision.DISPONIBLE);
        assertThat(mias.get(1).favorita()).isTrue();
        assertThat(delOtro).extracting(m -> m.situacion().estado())
                .containsExactly(EstadoMision.DISPONIBLE, EstadoMision.BLOQUEADA);
    }

    @Test
    @DisplayName("las destacadas son las que el jugador puede jugar; el detalle de una inexistente es 404")
    void destacadasYDetalle() {
        assertThat(consultar().destacadas(JUGADOR)).extracting(m -> m.mision().id()).containsExactly("templo-olvidado");
        assertThat(consultar().detalle(JUGADOR, "templo-olvidado").mision().nombre()).isEqualTo("El Templo Olvidado");
        assertThatThrownBy(() -> consultar().detalle(JUGADOR, "no-existe")).isInstanceOf(MisionNoEncontrada.class);
    }

    @Test
    @DisplayName("en curso: solo las del jugador; el reporte solo de las terminadas y propias")
    void enCursoYReporte() {
        Ejecucion enCurso = ejecuciones.guardar(Ejecucion.nueva(UUID.randomUUID(), "templo-olvidado", JUGADOR,
                Misiones.HEROE, List.of(), Escalon.NORMAL, AHORA.minus(Duration.ofHours(3)),
                Duration.ofHours(12), 1L, null));
        Ejecucion completada = terminada("segunda", true, AHORA.minus(Duration.ofDays(1)), List.of());

        assertThat(ejecucionesDe().enCurso(JUGADOR)).extracting(e -> e.ejecucion().id()).containsExactly(enCurso.id());
        assertThat(ejecucionesDe().enCurso(OTRO)).isEmpty();
        assertThat(ejecucionesDe().reporte(JUGADOR, completada.id()).mision().id()).isEqualTo("segunda");
        assertThatThrownBy(() -> ejecucionesDe().reporte(JUGADOR, enCurso.id())).isInstanceOf(SinReporteTodavia.class);
        assertThatThrownBy(() -> ejecucionesDe().reporte(OTRO, completada.id()))
                .isInstanceOf(EjecucionNoEncontrada.class);
    }

    @Test
    @DisplayName("el historial cuenta por categoria, guarda la coleccion de epicas y el progreso de la historia")
    void historial() {
        terminada("templo-olvidado", true, AHORA.minus(Duration.ofDays(3)),
                List.of(new RecompensasDeEjecucion.EpicaGanada("Velo de Sombras", "Sombra del Olvido", null)));
        terminada("templo-olvidado", false, AHORA.minus(Duration.ofDays(2)), List.of());
        terminada("exploracion-1", true, AHORA.minus(Duration.ofDays(1)), List.of());

        Historial historial = ejecucionesDe().historial(JUGADOR);

        assertThat(historial.terminadas()).hasSize(3);
        assertThat(historial.terminadas().getFirst().mision().id()).isEqualTo("exploracion-1");
        assertThat(historial.porCategoria()).containsExactly(
                new Historial.PorCategoria(Categoria.HISTORIA, 1, 1),
                new Historial.PorCategoria(Categoria.DESAFIO, 0, 0),
                new Historial.PorCategoria(Categoria.EXPLORACION, 1, 0));
        assertThat(historial.epicas()).extracting(Historial.EpicaObtenida::nombre).containsExactly("Velo de Sombras");
        assertThat(historial.mejoresTiempos()).hasSize(2);
        assertThat(historial.cadenas()).containsExactly(new Historial.Cadena("Historia", 1, 2));
    }

    @Test
    @DisplayName("una mision que ya no se publica no se pinta; sus epicas siguen en la coleccion")
    void misionRetiradaDeLaSemilla() {
        Ejecucion enCurso = ejecuciones.guardar(Ejecucion.nueva(UUID.randomUUID(), "retirada", JUGADOR,
                Misiones.HEROE, List.of(), Escalon.NORMAL, AHORA.minus(Duration.ofHours(1)),
                Duration.ofHours(12), 1L, null));
        Ejecucion terminada = terminada("retirada", true, AHORA.minus(Duration.ofDays(1)),
                List.of(new RecompensasDeEjecucion.EpicaGanada("Velo de Sombras", "Sombra del Olvido", null)));

        assertThat(ejecucionesDe().enCurso(JUGADOR)).isEmpty();
        assertThatThrownBy(() -> ejecucionesDe().reporte(JUGADOR, terminada.id()))
                .isInstanceOf(MisionNoEncontrada.class);
        Historial historial = ejecucionesDe().historial(JUGADOR);
        assertThat(historial.terminadas()).isEmpty();
        assertThat(historial.epicas()).extracting(Historial.EpicaObtenida::nombre).containsExactly("Velo de Sombras");
        assertThat(enCurso.estado()).isNotNull();
    }

    @Test
    @DisplayName("los intentos que quedan de un desafio en su periodo; nulo si la mision no los limita")
    void intentosRestantes() {
        Mision desafio = Misiones.desafio("desafio-diario",
                new nexus.misiones.dominio.Intentos(2, nexus.misiones.dominio.Periodo.DIARIO));
        catalogo = new Dobles.Catalogo(List.of(Misiones.templo(), desafio), List.of());
        ejecuciones.guardar(Ejecucion.nueva(UUID.randomUUID(), "desafio-diario", JUGADOR, Misiones.HEROE, List.of(),
                Escalon.NORMAL, AHORA.minus(Duration.ofHours(1)), Duration.ofHours(2), 1L, null));
        Ejecucion deAnteayer = Ejecucion.nueva(UUID.randomUUID(), "desafio-diario", JUGADOR, Misiones.HEROE,
                List.of(), Escalon.NORMAL, AHORA.minus(Duration.ofDays(2)), Duration.ofHours(2), 1L, null);
        deAnteayer.cancelar(AHORA.minus(Duration.ofDays(2)).plusSeconds(60));
        ejecuciones.guardar(deAnteayer);

        assertThat(consultar().intentosRestantes(JUGADOR, desafio)).as("ayer no cuenta hoy").isEqualTo(1);
        assertThat(consultar().intentosRestantes(OTRO, desafio)).isEqualTo(2);
        assertThat(consultar().intentosRestantes(JUGADOR, Misiones.templo())).isNull();
    }

    @Test
    @DisplayName("favoritas: marcar y desmarcar, idempotentes; una mision inexistente es 404")
    void favoritas() {
        GestionarFavoritas gestion = new GestionarFavoritas(catalogo, favoritas, reloj);

        gestion.marcar(JUGADOR, "templo-olvidado");
        gestion.marcar(JUGADOR, "templo-olvidado");
        assertThat(favoritas.delJugador(JUGADOR)).containsExactly("templo-olvidado");
        gestion.desmarcar(JUGADOR, "templo-olvidado");
        gestion.desmarcar(JUGADOR, "templo-olvidado");
        assertThat(favoritas.delJugador(JUGADOR)).isEmpty();
        assertThatThrownBy(() -> gestion.marcar(JUGADOR, "no-existe")).isInstanceOf(MisionNoEncontrada.class);
    }

    @Test
    @DisplayName("guardar una estrategia la valida con el prototipo y el nivel reales del heroe")
    void estrategias() {
        GestionarEstrategias gestion = new GestionarEstrategias(estrategias, inventario, productos, heroes, reloj);

        EstrategiaGuardada guardada = gestion.guardar(JUGADOR, "h-1", List.of(List.of("Embate sangriento")));

        assertThat(guardada.prototipo()).isEqualTo("Guerrero Armas");
        assertThat(guardada.nivel()).isEqualTo(1);
        assertThat(gestion.consultar(JUGADOR, "h-1")).isEqualTo(guardada);
        assertThat(heroes.validaciones).containsExactly("Guerrero Armas@1 [[Embate sangriento]]");
    }

    @Test
    @DisplayName("la estrategia de un heroe ajeno no se guarda ni se consulta")
    void estrategiaAjena() {
        GestionarEstrategias gestion = new GestionarEstrategias(estrategias, inventario, productos, heroes, reloj);

        assertThatThrownBy(() -> gestion.guardar(OTRO, "h-1", List.of())).isInstanceOf(HeroeNoEncontrado.class);
        assertThatThrownBy(() -> gestion.consultar(OTRO, "h-1")).isInstanceOf(EstrategiaNoGuardada.class);
    }

    @Test
    @DisplayName("una estrategia que heroes rechaza no se guarda")
    void estrategiaRechazada() {
        heroes.motivoDeRechazo = "No vale.";
        GestionarEstrategias gestion = new GestionarEstrategias(estrategias, inventario, productos, heroes, reloj);

        assertThatThrownBy(() -> gestion.guardar(JUGADOR, "h-1", List.of(List.of("Vulcano"))))
                .isInstanceOf(EstrategiaInvalida.class);
        assertThat(estrategias.guardadas).isEmpty();
    }
}
