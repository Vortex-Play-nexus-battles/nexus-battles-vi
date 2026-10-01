package nexus.misiones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import nexus.misiones.catalogo.CatalogoDeMisionesDesdeSemilla;
import nexus.misiones.dominio.Categoria;
import nexus.misiones.dominio.Dificultad;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.EstadoMision;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.RecompensasDeEjecucion;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-MIS-012 vista por quien juega: lo que el tablon, las destacadas y el
 * detalle ensenan cuando el catalogo es la semilla real que se publica (no
 * misiones de mentira). Es lo que ve el jugador en
 * {@code GET /api/v1/misiones?categoria=HISTORIA} y en el detalle de la mision;
 * la misma comprobacion contra HTTP y el contrato esta en
 * {@code ServicioDeMisionesIT} (necesita Docker).
 */
class TablonConLaSemillaTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T12:00:00Z");
    private static final String JUGADOR = "11111111-1111-4111-8111-111111111111";

    private Dobles.Ejecuciones ejecuciones;
    private ConsultarMisiones consultar;

    @BeforeEach
    void preparar() {
        ejecuciones = new Dobles.Ejecuciones();
        consultar = new ConsultarMisiones(CatalogoDeMisionesDesdeSemilla.cargar(false), ejecuciones,
                new Dobles.Favoritas(), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("el tablon de Historia lista «El Templo Olvidado» y despues «La Forja Sumergida»")
    void estaEnElTablon() {
        PaginaDeMisiones pagina = consultar.tablero(JUGADOR, Categoria.HISTORIA, null, null, null, 0);

        assertThat(pagina.misiones()).extracting(m -> m.mision().id())
                .containsExactly("templo-olvidado", "la-forja-sumergida");
        assertThat(pagina.total()).isEqualTo(2);
        assertThat(consultar.tablero(JUGADOR, Categoria.HISTORIA, Dificultad.DIFICIL, null, null, 0).misiones())
                .extracting(m -> m.mision().nombre()).containsExactly("La Forja Sumergida");
        assertThat(consultar.tablero(JUGADOR, Categoria.EXPLORACION, null, null, null, 0).misiones()).isEmpty();
    }

    @Test
    @DisplayName("sigue a «El Templo Olvidado»: bloqueada hasta completarlo, disponible despues")
    void seDesbloqueaConElTemplo() {
        var antes = consultar.detalle(JUGADOR, "la-forja-sumergida").situacion();
        assertThat(antes.estado()).isEqualTo(EstadoMision.BLOQUEADA);
        assertThat(antes.motivoBloqueo()).isEqualTo("Completa «El Templo Olvidado» para desbloquearla.");

        completarElTemplo();

        assertThat(consultar.detalle(JUGADOR, "la-forja-sumergida").situacion().estado())
                .isEqualTo(EstadoMision.DISPONIBLE);
        assertThat(consultar.tablero(JUGADOR, Categoria.HISTORIA, null, EstadoMision.DISPONIBLE, null, 0).misiones())
                .extracting(m -> m.mision().id()).contains("la-forja-sumergida");
    }

    @Test
    @DisplayName("el detalle ensena narrativa, objetivos, enemigos, jefe, Master y recompensas")
    void detalle() {
        var mision = consultar.detalle(JUGADOR, "la-forja-sumergida").mision();

        assertThat(mision.nombre()).isEqualTo("La Forja Sumergida");
        assertThat(mision.narrativa()).contains("Lago de Cristal Negro");
        assertThat(mision.objetivos()).hasSize(5);
        assertThat(mision.enemigos()).hasSize(3);
        assertThat(mision.jefe().nombre()).isEqualTo("El Herrero Ahogado");
        assertThat(mision.masters()).extracting(m -> m.nombre()).containsExactly("Hija de la Escarcha");
        assertThat(mision.recompensas().creditos()).isEqualTo(80);
    }

    @Test
    @DisplayName("el banner sigue siendo «El Templo Olvidado»: la del equipo no es destacada")
    void noEsDestacada() {
        assertThat(consultar.destacadas(JUGADOR)).extracting(m -> m.mision().id()).containsExactly("templo-olvidado");
    }

    private void completarElTemplo() {
        Instant inicio = AHORA.minus(Duration.ofDays(1));
        Ejecucion e = Ejecucion.nueva(UUID.randomUUID(), "templo-olvidado", JUGADOR, Misiones.HEROE, List.of(),
                Escalon.NORMAL, inicio, Duration.ofHours(12), 1L, null);
        e.terminar(new ResultadoDeMision(true, true, 3, true, 10, 5, 7, 1, List.of(), List.of(), List.of(), 70, 30,
                        List.of(3)),
                new RecompensasDeEjecucion(50, List.of(), List.of(), 30, List.of(), List.of(), true), false,
                inicio.plus(Duration.ofHours(12)));
        ejecuciones.guardar(e);
    }
}
