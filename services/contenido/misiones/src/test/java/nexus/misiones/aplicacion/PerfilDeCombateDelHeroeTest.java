package nexus.misiones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import java.util.List;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.simulacion.EstadisticasDeCombate;
import nexus.misiones.dominio.simulacion.Formula;
import nexus.misiones.dominio.simulacion.PerfilDeCombate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que el heroe lleva al combate, armado con lo que ya sabe el inventario
 * (estadisticas con equipo, lo que lleva puesto, sus epicas) y el catalogo de
 * productos (los nombres, que es como los entiende el motor).
 */
class PerfilDeCombateDelHeroeTest {

    private static final String JUGADOR = "11111111-1111-4111-8111-111111111111";
    private static final Formula ATAQUE = new Formula(11, 1, 6);
    private static final Formula DANO = new Formula(3, 1, 4);

    private Dobles.Inventario inventario;
    private Dobles.Productos productos;
    private Dobles.Heroes heroes;
    private PerfilDeCombateDelHeroe perfiles;

    @BeforeEach
    void preparar() {
        inventario = new Dobles.Inventario();
        productos = new Dobles.Productos();
        heroes = new Dobles.Heroes();
        perfiles = new PerfilDeCombateDelHeroe(inventario, productos, heroes);
    }

    private PerfilDeCombate perfil() {
        return perfiles.de(JUGADOR, Misiones.HEROE);
    }

    @Test
    @DisplayName("las estadisticas son las del inventario, con el equipo aplicado y sus formulas")
    void estadisticasConEquipo() {
        inventario.estadisticasDelHeroe = new InventarioDeHeroes.EstadisticasDelHeroe(12, 60, 14, ATAQUE, DANO, null);

        assertThat(perfil().estadisticas()).isEqualTo(new EstadisticasDeCombate(12, 60, 14, ATAQUE, DANO, null));
    }

    @Test
    @DisplayName("si el inventario no publica formulas se toman las del catalogo en su nivel, con el equipo plano del inventario")
    void formulasDelCatalogo() {
        inventario.estadisticasDelHeroe = new InventarioDeHeroes.EstadisticasDelHeroe(12, 60, 14);

        assertThat(perfil().estadisticas())
                .isEqualTo(new EstadisticasDeCombate(12, 60, 14, heroes.ataqueDeNivel, heroes.danoDeNivel, null));
    }

    @Test
    @DisplayName("sin formulas en ninguna parte no se mandan estadisticas: sin ataque el motor no dejaria golpear al heroe")
    void sinFormulas() {
        inventario.estadisticasDelHeroe = new InventarioDeHeroes.EstadisticasDelHeroe(12, 60, 14);
        heroes.ataqueDeNivel = null;
        heroes.danoDeNivel = null;

        assertThat(perfil().estadisticas()).isNull();
    }

    @Test
    @DisplayName("el equipo y las epicas van por su nombre, que es como los conoce el motor")
    void nombresDelEquipo() {
        inventario.equipoDelHeroe = new InventarioDeHeroes.EquipoDelHeroe(List.of("p-espada", "p-peto"),
                List.of("p-epica-1", "p-epica-2"));
        productos.nombres.put("p-espada", "Espada de una mano");
        productos.nombres.put("p-peto", "Peto de cuero");
        productos.nombres.put("p-epica-1", "Golpe de defensa");
        productos.nombres.put("p-epica-2", "Segundo impulso");

        PerfilDeCombate perfil = perfil();

        assertThat(perfil.equipamiento()).containsExactly("Espada de una mano", "Peto de cuero");
        assertThat(perfil.epicas()).containsExactly("Golpe de defensa", "Segundo impulso");
    }

    @Test
    @DisplayName("un producto que el catalogo ya no tiene no se manda: no hay nombre que aplicar")
    void productoSinNombre() {
        inventario.equipoDelHeroe = new InventarioDeHeroes.EquipoDelHeroe(List.of("p-espada", "p-fantasma"),
                List.of("p-fantasma"));
        productos.nombres.put("p-espada", "Espada de una mano");

        PerfilDeCombate perfil = perfil();

        assertThat(perfil.equipamiento()).containsExactly("Espada de una mano");
        assertThat(perfil.epicas()).isEmpty();
    }

    @Test
    @DisplayName("una epica repetida cuenta una vez y se mandan hasta ocho, como admite el motor")
    void epicasSinRepetirYConTope() {
        List<String> ids = new java.util.ArrayList<>(List.of("p-e0", "p-e0"));
        for (int i = 1; i <= 9; i++) {
            ids.add("p-e" + i);
        }
        inventario.equipoDelHeroe = new InventarioDeHeroes.EquipoDelHeroe(List.of(), ids);
        for (int i = 0; i <= 9; i++) {
            productos.nombres.put("p-e" + i, "Epica " + i);
        }

        assertThat(perfil().epicas()).hasSize(8).doesNotHaveDuplicates().startsWith("Epica 0", "Epica 1");
    }

    @Test
    @DisplayName("el equipo se manda hasta diez piezas, como admite el motor, y cada producto se pide una vez")
    void equipoConTope() {
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add("p-" + (i % 6));
        }
        inventario.equipoDelHeroe = new InventarioDeHeroes.EquipoDelHeroe(ids, List.of());
        for (int i = 0; i < 6; i++) {
            productos.nombres.put("p-" + i, "Pieza " + i);
        }

        PerfilDeCombate perfil = perfil();

        assertThat(perfil.equipamiento()).hasSize(10);
        assertThat(productos.consultadas).hasSize(6).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("si el inventario no conoce al heroe, pelea con lo del catalogo: sin estadisticas ni equipo")
    void heroeDesconocido() {
        inventario.fallarAlPedirEstadisticas = new HeroeNoEncontrado();
        inventario.fallarAlPedirEquipo = new HeroeNoEncontrado();

        PerfilDeCombate perfil = perfil();

        assertThat(perfil).isEqualTo(PerfilDeCombate.delCatalogo());
    }

    @Test
    @DisplayName("lo que se sepa se aprovecha: sin estadisticas pero con equipo, el equipo se manda")
    void soloEquipo() {
        inventario.fallarAlPedirEstadisticas = new HeroeNoEncontrado();
        inventario.equipoDelHeroe = new InventarioDeHeroes.EquipoDelHeroe(List.of("p-espada"), List.of());
        productos.nombres.put("p-espada", "Espada de una mano");

        PerfilDeCombate perfil = perfil();

        assertThat(perfil.estadisticas()).isNull();
        assertThat(perfil.equipamiento()).containsExactly("Espada de una mano");
    }

    @Test
    @DisplayName("si el inventario o productos no responden el fallo sube: la simulacion se reintenta, no pelea a medias")
    void dependenciaCaida() {
        inventario.fallarAlPedirEquipo = Dobles.caido("inventario");
        assertThatThrownBy(this::perfil).isInstanceOf(DependenciaDegradada.class);

        inventario.fallarAlPedirEquipo = null;
        inventario.equipoDelHeroe = new InventarioDeHeroes.EquipoDelHeroe(List.of("p-espada"), List.of());
        productos.fallarAlPedirNombre = Dobles.caido("productos");
        assertThatThrownBy(this::perfil).isInstanceOf(DependenciaDegradada.class);

        productos.fallarAlPedirNombre = null;
        inventario.fallarAlPedirEstadisticas = Dobles.caido("inventario");
        assertThatThrownBy(this::perfil).isInstanceOf(DependenciaDegradada.class);
    }
}
