package nexus.inventario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.HeroeEnMisionException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TablaDeNiveles;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El heroe en mision, de punta a punta sobre el repositorio (inventario.yaml 1.6.0, B9). */
class GestionarBloqueoMisionTest {

    private static final UUID PROPIETARIO = UUID.fromString("ae8df97e-9ab9-4af5-bd2a-25715919e5f1");
    private static final UUID EJECUCION = UUID.fromString("0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11");
    private static final UUID OTRA = UUID.fromString("5e2b8c1a-7d3f-4c11-8a2e-9b0c4d5e6f70");

    private RepositorioInventariosEnMemoria repositorio;
    private final AtomicInteger consultasDeTabla = new AtomicInteger();
    private boolean heroesCaido;
    private GestionarBloqueoMision gestion;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        repositorio.guardar(Inventario.vacio(PROPIETARIO.toString())
                .agregar(new ElementoInventario("heroe-1", "producto-heroe", TipoElementoInventario.HEROE, "Vorn")));
        gestion = new GestionarBloqueoMision(repositorio, () -> {
            consultasDeTabla.incrementAndGet();
            if (heroesCaido) {
                throw new ProgresionNoDisponibleException("heroes no responde", null);
            }
            List<Double> paraSubir = new ArrayList<>();
            for (int nivel = 1; nivel <= 7; nivel++) {
                paraSubir.add(100 * Math.pow(1.2, nivel - 1));
            }
            return new TablaDeNiveles(paraSubir);
        });
    }

    private ElementoInventario guardado() {
        return repositorio.buscarPorElementoId("heroe-1").orElseThrow().elemento("heroe-1");
    }

    @Test
    @DisplayName("bloquear persiste la ejecucion y el heroe deja de estar disponible")
    void bloquear() {
        ElementoInventario bloqueado = gestion.bloquear(PROPIETARIO, "heroe-1", EJECUCION, "mision-x-bloqueo");

        assertThat(bloqueado.disponible()).isFalse();
        assertThat(guardado().ejecucionMisionId()).isEqualTo(EJECUCION.toString());
        assertThat(gestion.bloquear(PROPIETARIO, "heroe-1", EJECUCION, "mision-x-bloqueo")).isEqualTo(bloqueado);
    }

    @Test
    @DisplayName("el heroe de otro jugador, uno inexistente o sin clave no se tocan")
    void rechazos() {
        assertThatThrownBy(() -> gestion.bloquear(UUID.randomUUID(), "heroe-1", EJECUCION, "k"))
                .isInstanceOf(InventarioAjenoException.class);
        assertThatThrownBy(() -> gestion.bloquear(PROPIETARIO, "no-existe", EJECUCION, "k"))
                .isInstanceOf(ElementoNoEncontradoException.class);
        assertThatThrownBy(() -> gestion.bloquear(PROPIETARIO, "heroe-1", EJECUCION, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gestion.liberar(PROPIETARIO, "heroe-1", EJECUCION, -1, "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(guardado().disponible()).isTrue();
    }

    @Test
    @DisplayName("liberar suma la experiencia y sube de nivel en la misma escritura; repetir no suma dos veces")
    void liberarConExperiencia() {
        gestion.bloquear(PROPIETARIO, "heroe-1", EJECUCION, "b");

        ElementoInventario liberado = gestion.liberar(PROPIETARIO, "heroe-1", EJECUCION, 130, "l");
        ElementoInventario repetido = gestion.liberar(PROPIETARIO, "heroe-1", EJECUCION, 130, "l");

        assertThat(liberado.disponible()).isTrue();
        assertThat(liberado.nivel()).isEqualTo(2);
        assertThat(liberado.experiencia()).isEqualTo(30, within(1e-9));
        assertThat(repetido).isEqualTo(liberado);
        assertThat(guardado().nivel()).isEqualTo(2);
    }

    @Test
    @DisplayName("sin experiencia (cancelar) no hace falta heroes")
    void liberarSinExperiencia() {
        gestion.bloquear(PROPIETARIO, "heroe-1", EJECUCION, "b");
        heroesCaido = true;

        ElementoInventario liberado = gestion.liberar(PROPIETARIO, "heroe-1", EJECUCION, 0, "l");

        assertThat(liberado.disponible()).isTrue();
        assertThat(liberado.nivel()).isEqualTo(1);
        assertThat(consultasDeTabla).hasValue(0);
    }

    @Test
    @DisplayName("si heroes no responde no se aplica nada y el heroe sigue en mision")
    void heroesCaido() {
        gestion.bloquear(PROPIETARIO, "heroe-1", EJECUCION, "b");
        heroesCaido = true;

        assertThatThrownBy(() -> gestion.liberar(PROPIETARIO, "heroe-1", EJECUCION, 50, "l"))
                .isInstanceOf(ProgresionNoDisponibleException.class);
        assertThat(guardado().enMision()).isTrue();
        assertThat(guardado().experiencia()).isZero();
    }

    @Test
    @DisplayName("la liberacion de otra ejecucion no suelta al heroe")
    void otraEjecucion() {
        gestion.bloquear(PROPIETARIO, "heroe-1", EJECUCION, "b");

        assertThatThrownBy(() -> gestion.liberar(PROPIETARIO, "heroe-1", OTRA, 10, "l"))
                .isInstanceOf(HeroeEnMisionException.class);
        assertThat(guardado().ejecucionMisionId()).isEqualTo(EJECUCION.toString());
    }
}
