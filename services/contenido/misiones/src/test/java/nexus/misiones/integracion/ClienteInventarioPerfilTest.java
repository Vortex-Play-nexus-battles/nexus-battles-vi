package nexus.misiones.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import nexus.misiones.DependenciasFalsas;
import nexus.misiones.aplicacion.HeroeNoEncontrado;
import nexus.misiones.aplicacion.InventarioDeHeroes;
import nexus.misiones.dominio.simulacion.Formula;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que misiones lee del inventario para armar al heroe que entra al motor
 * (HU-SIM-003): sus estadisticas en su nivel con el equipo y las formulas
 * (inventario.yaml 1.6.0, {@code GET .../heroes/{id}/estadisticas}), y lo que
 * lleva puesto y sus epicas ({@code GET .../equipamiento} y la vitrina).
 */
class ClienteInventarioPerfilTest {

    private static final String JUGADOR = "11111111-1111-4111-8111-111111111111";

    private DependenciasFalsas falsas;
    private ClienteInventario cliente;

    @BeforeEach
    void arrancar() throws IOException {
        falsas = new DependenciasFalsas();
        cliente = new ClienteInventario(ClientesDePrueba.rest(), falsas.base(), ClientesDePrueba.corta("inventario"),
                ClientesDePrueba.corta("inventario-entregas"));
        falsas.conHeroe("h-1", JUGADOR, "p-heroe", "Guerrero Armas");
    }

    @AfterEach
    void parar() {
        falsas.close();
    }

    @Test
    @DisplayName("las estadisticas traen poder, vida y defensa con el equipo, y las formulas de ataque y dano")
    void estadisticasConFormulas() {
        InventarioDeHeroes.EstadisticasDelHeroe estadisticas = cliente.estadisticas(JUGADOR, "h-1");

        assertThat(estadisticas).isEqualTo(new InventarioDeHeroes.EstadisticasDelHeroe(10, 44, 11,
                new Formula(11, 1, 6), new Formula(3, 1, 4), null));
        // La identidad viaja en la cabecera, nunca en la ruta.
        assertThat(falsas.a("GET", "/api/v1/inventario/heroes/h-1/estadisticas").getFirst().cabecera("X-User-Name"))
                .isEqualTo(JUGADOR);
    }

    @Test
    @DisplayName("un inventario que no publica formulas no rompe: llegan nulas")
    void estadisticasSinFormulas() {
        falsas.estadisticasSinFormulas = true;

        InventarioDeHeroes.EstadisticasDelHeroe estadisticas = cliente.estadisticas(JUGADOR, "h-1");

        assertThat(estadisticas.ataque()).isNull();
        assertThat(estadisticas.dano()).isNull();
        assertThat(estadisticas.poder()).isEqualTo(10);
    }

    @Test
    @DisplayName("el equipo puesto y las epicas disponibles salen del equipamiento y la vitrina, recorriendola entera")
    void equipoYEpicas() {
        falsas.heroes.get("h-1").armas = List.of("arma-1", "arma-2");
        falsas.heroes.get("h-1").armaduras = Map.of("PECHO", "armadura-1");
        falsas.conElemento("arma-1", "p-espada", "ARMA", true);
        falsas.conElemento("arma-2", "p-daga", "ARMA", true);
        falsas.conElemento("armadura-1", "p-peto", "ARMADURA", true);
        falsas.conElemento("sin-poner", "p-otra-espada", "ARMA", true);
        falsas.conElemento("epica-1", "p-epica-a", "EPICA", true);
        // Una epica retenida por una subasta no se puede usar.
        falsas.conElemento("epica-2", "p-epica-b", "EPICA", false);

        InventarioDeHeroes.EquipoDelHeroe equipo = cliente.equipo(JUGADOR, "h-1");

        assertThat(equipo.productosEquipados()).containsExactlyInAnyOrder("p-espada", "p-daga", "p-peto");
        assertThat(equipo.productosDeEpicas()).containsExactly("p-epica-a");
        // 7 elementos de la vitrina en paginas de 2: se pidieron las cuatro, con la identidad en la cabecera.
        List<DependenciasFalsas.Peticion> paginas = falsas.a("GET", "/api/v1/inventario/elementos").stream()
                .filter(p -> p.consulta() != null && p.consulta().contains("pagina=")).toList();
        assertThat(paginas).hasSize(4);
        assertThat(paginas).allSatisfy(p -> assertThat(p.cabecera("X-User-Name")).isEqualTo(JUGADOR));
    }

    @Test
    @DisplayName("sin nada puesto no hay equipo, pero las epicas del jugador siguen contando")
    void sinEquipo() {
        falsas.heroes.get("h-1").equipado = false;
        falsas.conElemento("epica-1", "p-epica-a", "EPICA", true);

        InventarioDeHeroes.EquipoDelHeroe equipo = cliente.equipo(JUGADOR, "h-1");

        assertThat(equipo.productosEquipados()).isEmpty();
        assertThat(equipo.productosDeEpicas()).containsExactly("p-epica-a");
    }

    @Test
    @DisplayName("un elemento equipado que ya no esta en la vitrina no se manda: no se sabe que es")
    void elementoFueraDeLaVitrina() {
        falsas.heroes.get("h-1").armas = List.of("arma-1", "arma-perdida");
        falsas.conElemento("arma-1", "p-espada", "ARMA", true);

        assertThat(cliente.equipo(JUGADOR, "h-1").productosEquipados()).containsExactly("p-espada");
    }

    @Test
    @DisplayName("el heroe de otro jugador es un heroe no encontrado")
    void heroeAjeno() {
        assertThatThrownBy(() -> cliente.equipo("22222222-2222-4222-8222-222222222222", "h-1"))
                .isInstanceOf(HeroeNoEncontrado.class);
        assertThatThrownBy(() -> cliente.estadisticas("22222222-2222-4222-8222-222222222222", "h-1"))
                .isInstanceOf(HeroeNoEncontrado.class);
    }

    @Test
    @DisplayName("si el inventario no responde, el fallo sube como dependencia degradada")
    void inventarioCaido() {
        falsas.caidas.add("inventario");

        assertThatThrownBy(() -> cliente.equipo(JUGADOR, "h-1")).isInstanceOf(DependenciaDegradada.class);
    }
}
