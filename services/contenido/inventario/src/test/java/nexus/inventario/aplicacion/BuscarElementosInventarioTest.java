package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuscarElementosInventarioTest {

    /** Lo que el catálogo sabe de cada producto (descripción, efectos, acciones). */
    private static final Map<String, String> CATALOGO = Map.of(
            "producto-casco", "Corona de hielo · Armadura · Casco · +1 a la defensa, +1 de vida",
            "producto-espada", "Espada de una mano · Arma · +1 al ataque, +1 % de crítico",
            "producto-mago", "Mago Fuego · Héroe · Misiles de magma · Vulcano",
            "producto-ajeno", "Pluma sanadora · Ítem · Mejora la sanación multiplicando x2");

    private RepositorioInventariosEnMemoria repositorio;
    private BuscarElementosInventario busqueda;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        IndiceDeNgramas indice = IndiceDeNgramas.construir(CATALOGO);
        busqueda = new BuscarElementosInventario(repositorio, indice::buscar);
        repositorio.guardar(Inventario.vacio("jugador-A")
                .agregar(new ElementoInventario(
                        "elemento-1", "producto-casco", TipoElementoInventario.ARMADURA,
                        "Casco de Bruma", ParteArmadura.CASCO))
                .agregar(new ElementoInventario(
                        "elemento-2", "producto-espada", TipoElementoInventario.ARMA,
                        "Espada Solar"))
                .agregar(new ElementoInventario(
                        "elemento-3", "producto-mago", TipoElementoInventario.HEROE,
                        "Ignis")));
        repositorio.guardar(Inventario.vacio("jugador-B")
                .agregar(new ElementoInventario(
                        "elemento-9", "producto-ajeno", TipoElementoInventario.ITEM,
                        "Bruma ajena")));
    }

    @Test
    @DisplayName("localiza un elemento propio por un fragmento de su nombre propio")
    void buscarPorNombrePropio() {
        PaginaInventario pagina = busqueda.buscar("jugador-A", "brum", 0);

        assertEquals(1, pagina.totalElementos());
        assertEquals("elemento-1", pagina.elementos().getFirst().id());
        assertEquals(16, pagina.tamanio());
    }

    @Test
    @DisplayName("§7.1: encuentra por la información del producto (efectos, descripción)")
    void buscarPorInformacionDelProducto() {
        assertEquals(List.of("elemento-1"), ids(busqueda.buscar("jugador-A", "defensa", 0)));
        assertEquals(List.of("elemento-2"), ids(busqueda.buscar("jugador-A", "crítico", 0)));
    }

    @Test
    @DisplayName("§7.1: un héroe se encuentra por una de sus acciones (habilidades)")
    void buscarHeroePorSuAccion() {
        assertEquals(List.of("elemento-3"), ids(busqueda.buscar("jugador-A", "vulc", 0)));
    }

    @Test
    @DisplayName("encuentra por el tipo y la parte, también en su nombre legible")
    void buscarPorTipoYParte() {
        assertEquals(List.of("elemento-1"), ids(busqueda.buscar("jugador-A", "armadura", 0)));
        assertEquals(List.of("elemento-1"), ids(busqueda.buscar("jugador-A", "casco", 0)));
        assertEquals(List.of("elemento-3"), ids(busqueda.buscar("jugador-A", "héroe", 0)));
    }

    @Test
    @DisplayName("la busqueda tolera mayusculas y tildes")
    void buscarSinDistinguirFormato() {
        assertEquals("elemento-1", busqueda.buscar("jugador-A", "BRÚMA", 0)
                .elementos().getFirst().id());
    }

    @Test
    @DisplayName("nunca devuelve coincidencias del inventario de otro jugador")
    void noExponeInventarioAjeno() {
        assertTrue(busqueda.buscar("jugador-A", "ajeno", 0).elementos().isEmpty());
        // «sanación» está en el producto que solo tiene el jugador B.
        assertTrue(busqueda.buscar("jugador-A", "sanacion", 0).elementos().isEmpty());
    }

    @Test
    @DisplayName("sin catálogo la búsqueda sigue en los datos propios del elemento")
    void sinCatalogo() {
        BuscarElementosInventario sinCatalogo = new BuscarElementosInventario(repositorio);

        assertEquals(List.of("elemento-1"), ids(sinCatalogo.buscar("jugador-A", "bruma", 0)));
        assertTrue(sinCatalogo.buscar("jugador-A", "defensa", 0).elementos().isEmpty());
    }

    @Test
    @DisplayName("un jugador sin inventario recibe una página vacía, no un error")
    void sinInventario() {
        PaginaInventario pagina = busqueda.buscar("jugador-nuevo", "bruma", 0);

        assertEquals(0, pagina.totalElementos());
        assertTrue(pagina.ultima());
    }

    @Test
    @DisplayName("los resultados dicen qué héroe lleva cada objeto")
    void resultadosConPortador() {
        repositorio.guardar(repositorio.buscarPorPropietario("jugador-A").orElseThrow()
                .equipar("elemento-3", "elemento-2"));

        PaginaInventario pagina = busqueda.buscar("jugador-A", "espada", 0);

        assertEquals(new EquipadoEn("elemento-3", "Ignis"), pagina.equipadoEn("elemento-2"));
        assertNull(busqueda.buscar("jugador-A", "bruma", 0).equipadoEn("elemento-1"));
    }

    @Test
    @DisplayName("rechaza criterios con menos de cuatro caracteres")
    void exigeCuatroCaracteres() {
        assertThrows(CriterioBusquedaInvalidoException.class,
                () -> busqueda.buscar("jugador-A", "abc", 0));
        assertThrows(CriterioBusquedaInvalidoException.class,
                () -> busqueda.buscar("jugador-A", "   ", 0));
    }

    @Test
    @DisplayName("exige identidad y un numero de pagina valido")
    void validaIdentidadYPagina() {
        assertThrows(IdentidadRequeridaException.class,
                () -> busqueda.buscar(null, "bruma", 0));
        assertThrows(IllegalArgumentException.class,
                () -> busqueda.buscar("jugador-A", "bruma", -1));
    }

    private static List<String> ids(PaginaInventario pagina) {
        return pagina.elementos().stream().map(ElementoInventario::id).toList();
    }
}
