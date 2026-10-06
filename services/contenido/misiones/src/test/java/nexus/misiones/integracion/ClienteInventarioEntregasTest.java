package nexus.misiones.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import nexus.misiones.DependenciasFalsas;
import nexus.misiones.aplicacion.InventarioDeHeroes.ProductoAEntregar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Epica unica (inventario 1.7.0): al entregar una epica que el jugador ya
 * tiene, el inventario responde 201 con la epica en {@code yaTenia}. Misiones
 * lo lee para poder decirselo al jugador; un inventario anterior, que no trae
 * el campo, se lee como «nada que ya tuviera».
 */
class ClienteInventarioEntregasTest {

    private static final String EPICA = "4481eb34-384a-3fa0-ba9a-1aac9562c38f";

    private DependenciasFalsas falsas;
    private ClienteInventario inventario;
    private final UUID ejecucion = UUID.randomUUID();

    @BeforeEach
    void arrancar() throws IOException {
        falsas = new DependenciasFalsas();
        inventario = new ClienteInventario(ClientesDePrueba.rest(), falsas.base(),
                ClientesDePrueba.corta("inventario"), ClientesDePrueba.corta("inventario-entregas"));
    }

    @AfterEach
    void parar() {
        falsas.close();
    }

    @Test
    @DisplayName("una epica nueva se entrega y no hay nada que ya tuviera")
    void epicaNueva() {
        List<String> yaTenia = inventario.entregar("uid-1", ejecucion, List.of(new ProductoAEntregar(EPICA, 1)),
                "mision-" + ejecucion + "-epica");

        assertThat(yaTenia).isEmpty();
    }

    @Test
    @DisplayName("si el inventario dice que ya la tenia, entregar lo devuelve y no falla")
    void yaLaTenia() {
        falsas.epicasQueYaTiene.add(EPICA);

        List<String> yaTenia = inventario.entregar("uid-1", ejecucion,
                List.of(new ProductoAEntregar(EPICA, 1), new ProductoAEntregar("otra", 1)),
                "mision-" + ejecucion + "-epica");

        assertThat(yaTenia).containsExactly(EPICA);
    }
}
