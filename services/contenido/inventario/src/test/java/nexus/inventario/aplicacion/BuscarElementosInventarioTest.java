package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuscarElementosInventarioTest {

    private RepositorioInventariosEnMemoria repositorio;
    private BuscarElementosInventario busqueda;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        busqueda = new BuscarElementosInventario(repositorio);
        repositorio.guardar(Inventario.vacio("jugador-A")
                .agregar(new ElementoInventario(
                        "elemento-1", "producto-casco", TipoElementoInventario.ARMADURA,
                        "Casco de Bruma", ParteArmadura.CASCO))
                .agregar(new ElementoInventario(
                        "elemento-2", "producto-espada", TipoElementoInventario.ARMA,
                        "Espada Solar")));
        repositorio.guardar(Inventario.vacio("jugador-B")
                .agregar(new ElementoInventario(
                        "elemento-3", "producto-ajeno", TipoElementoInventario.ITEM,
                        "Bruma ajena")));
    }

    @Test
    @DisplayName("localiza un elemento propio por su informacion registrada")
    void buscarPorInformacionRegistrada() {
        PaginaInventario pagina = busqueda.buscar("jugador-A", "bruma", 0);

        assertEquals(1, pagina.totalElementos());
        assertEquals("elemento-1", pagina.elementos().getFirst().id());
        assertEquals(16, pagina.tamanio());
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
}
