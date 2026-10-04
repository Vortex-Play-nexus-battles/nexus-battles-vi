package nexus.misiones.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import java.io.IOException;
import java.util.UUID;
import nexus.misiones.DependenciasFalsas;
import nexus.misiones.aplicacion.InventarioDeHeroes;
import nexus.misiones.aplicacion.RechazoDelServicio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FASE 2 (sin bloqueos para siempre): la liberacion del heroe solo se da por
 * perdida cuando el inventario dice que no. Una credencial de servicio que no
 * vale (401: paso con la clave de firma del emisor, #750), un tiempo agotado
 * (408) o un cupo (429) dejaban el paso FALLIDO y al heroe En mision para
 * siempre; ahora son falta de respuesta y la liquidacion los reintenta.
 */
class ClienteInventarioLiberacionTest {

    private DependenciasFalsas falsas;
    private ClienteInventario inventario;
    private final UUID ejecucion = UUID.randomUUID();

    @BeforeEach
    void arrancar() throws IOException {
        falsas = new DependenciasFalsas();
        inventario = new ClienteInventario(ClientesDePrueba.rest(), falsas.base(),
                ClientesDePrueba.corta("inventario"), ClientesDePrueba.corta("inventario-entregas"));
        falsas.conHeroe("h-1", "uid-1", "p-armas", "Guerrero Armas").bloqueadoPor = ejecucion.toString();
    }

    @AfterEach
    void parar() {
        falsas.close();
    }

    @Test
    @DisplayName("liberar devuelve la progresion y el heroe queda libre")
    void libera() {
        InventarioDeHeroes.ProgresionDelHeroe progresion = inventario.liberar("h-1", "uid-1", ejecucion, 30);

        assertThat(progresion.nivel()).isEqualTo(1);
        assertThat(progresion.experiencia()).isEqualTo(30.0);
        assertThat(falsas.heroes.get("h-1").bloqueadoPor).isNull();
    }

    @Test
    @DisplayName("401, 408 y 429 al liberar no son un «no»: se reintentan y el heroe no se pierde")
    void pasajerosNoSonDefinitivos() {
        for (int estado : new int[] {401, 408, 429}) {
            falsas.forzados.put("/liberacion", estado);
            assertThatThrownBy(() -> inventario.liberar("h-1", "uid-1", ejecucion, 30))
                    .as("HTTP %d", estado)
                    .isInstanceOf(DependenciaDegradada.class);
        }
        falsas.forzados.clear();

        assertThat(inventario.liberar("h-1", "uid-1", ejecucion, 30).experiencia()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("un 403 (no es su heroe) o un 409 (lo bloquea otra ejecucion) siguen siendo definitivos")
    void rechazosDefinitivos() {
        for (int estado : new int[] {403, 409}) {
            falsas.forzados.put("/liberacion", estado);
            assertThatThrownBy(() -> inventario.liberar("h-1", "uid-1", ejecucion, 30))
                    .as("HTTP %d", estado)
                    .isInstanceOf(RechazoDelServicio.class);
        }
    }
}
