package nexus.misiones.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import nexus.misiones.DependenciasFalsas;
import nexus.misiones.aplicacion.ServicioDeHeroes;
import nexus.misiones.dominio.simulacion.Formula;
import nexus.misiones.dominio.simulacion.TurnoParaDecidir;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las dos lecturas de HU-SIM-003 que no son del inventario: el nombre de un
 * producto (como lo conoce el motor) y lo que heroes aporta al combate de
 * mision: las formulas de un prototipo en su nivel y la decision de la IA.
 */
class ClienteProductosYHeroesTest {

    private DependenciasFalsas falsas;
    private ClienteProductos productos;
    private ClienteHeroes heroes;

    @BeforeEach
    void arrancar() throws IOException {
        falsas = new DependenciasFalsas();
        productos = new ClienteProductos(ClientesDePrueba.rest(), falsas.base(), ClientesDePrueba.corta("productos"));
        heroes = new ClienteHeroes(ClientesDePrueba.rest(), falsas.base(), ClientesDePrueba.corta("heroes"));
    }

    @AfterEach
    void parar() {
        falsas.close();
    }

    // ---------------------------------------------------------- productos

    @Test
    @DisplayName("el nombre de un arma, una armadura, un item o una epica es el del catalogo de productos")
    void nombreDeUnProducto() {
        falsas.nombresDeProducto.put("p-espada", "Espada de una mano");

        assertThat(productos.nombreDe("p-espada")).isEqualTo("Espada de una mano");
    }

    @Test
    @DisplayName("un producto que el catalogo no tiene no tiene nombre: nulo, no un error")
    void productoInexistente() {
        assertThat(productos.nombreDe("p-fantasma")).isNull();
    }

    @Test
    @DisplayName("si productos no responde, el fallo sube como dependencia degradada")
    void productosCaido() {
        falsas.caidas.add("productos");

        assertThatThrownBy(() -> productos.nombreDe("p-espada")).isInstanceOf(DependenciaDegradada.class);
    }

    // ------------------------------------------------------------- heroes

    @Test
    @DisplayName("la vista por nivel trae tambien las formulas del prototipo, para mantener la vida y la defensa de la semilla")
    void vistaConFormulas() {
        ServicioDeHeroes.EstadisticasDeNivel vista = heroes.enNivel("Guerrero Armas", 3);

        assertThat(vista).isEqualTo(new ServicioDeHeroes.EstadisticasDeNivel(10, 20, 5, new Formula(9, 1, 6),
                new Formula(1, 1, 4), null));
    }

    private TurnoParaDecidir turnoConPoder(int poder) {
        return new TurnoParaDecidir("Guerrero Armas", 1, List.of(List.of("Embate sangriento")), 1, poder, 44,
                Map.of(), List.of());
    }

    @SuppressWarnings("unchecked")
    private int poderEnviadoAHeroes() {
        Map<String, Object> estado = (Map<String, Object>) falsas.a("POST", "/api/v1/estrategias/decision")
                .getLast().json().get("estado");
        return ((Number) estado.get("poder")).intValue();
    }

    @Test
    @DisplayName("heroes no admite mas poder que el maximo de su catalogo: con equipo que suma poder se acota")
    void poderAcotadoAlDelCatalogo() {
        // La vista falsa dice que el prototipo tiene 10 de poder maximo.
        heroes.decidir(turnoConPoder(14));

        assertThat(poderEnviadoAHeroes()).isEqualTo(10);
    }

    @Test
    @DisplayName("un poder por debajo del maximo viaja tal cual")
    void poderNormal() {
        heroes.decidir(turnoConPoder(6));

        assertThat(poderEnviadoAHeroes()).isEqualTo(6);
    }

    @Test
    @DisplayName("la decision trae la rotacion de la que salio la accion, para saber de cual avanzar el cursor")
    void decisionConRotacion() {
        assertThat(heroes.decidir(turnoConPoder(6)).rotacion()).isEqualTo(1);
    }

    @Test
    @DisplayName("sin rotaciones no se pregunta nada: es ataque basico siempre")
    void sinRotacionesNoPregunta() {
        TurnoParaDecidir sinEstrategia = new TurnoParaDecidir("Guerrero Armas", 1, List.of(), 1, 14, 44, Map.of(),
                List.of());

        assertThat(heroes.decidir(sinEstrategia).esAtaqueBasico()).isTrue();
        assertThat(falsas.a("POST", "/api/v1/estrategias/decision")).isEmpty();
        assertThat(falsas.a("GET", "/api/v1/heroes")).isEmpty();
    }
}
