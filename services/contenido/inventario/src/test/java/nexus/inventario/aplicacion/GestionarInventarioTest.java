package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GestionarInventarioTest {

    private RepositorioInventariosEnMemoria repositorio;
    private GestionarInventario gestion;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        gestion = new GestionarInventario(repositorio);
    }

    @Test
    @DisplayName("crear usa la identidad autenticada como propietario")
    void crearElementoPropio() {
        ElementoInventario creado = gestion.crear(
                "jugador-A", "producto-1", TipoElementoInventario.ITEM, "Amuleto de Niebla");

        Inventario inventario = repositorio.buscarPorPropietario("jugador-A").orElseThrow();
        assertEquals(creado, inventario.elementos().getFirst());
    }

    @Test
    @DisplayName("el propietario puede modificar el nombre de su elemento")
    void modificarElementoPropio() {
        ElementoInventario creado = gestion.crear(
                "jugador-A", "producto-1", TipoElementoInventario.ITEM, "Amuleto de Niebla");

        ElementoInventario modificado = gestion.modificarNombre(
                "jugador-A", creado.id(), "Amuleto de Bruma");

        assertEquals("Amuleto de Bruma", modificado.nombrePropio());
        assertEquals("Amuleto de Bruma", repositorio.buscarPorPropietario("jugador-A")
                .orElseThrow().elementos().getFirst().nombrePropio());
    }

    @Test
    @DisplayName("un jugador no puede modificar el elemento de otro")
    void rechazarModificacionAjena() {
        ElementoInventario elementoDeB = gestion.crear(
                "jugador-B", "producto-1", TipoElementoInventario.ITEM, "Daga Corta");

        assertThrows(InventarioAjenoException.class,
                () -> gestion.modificarNombre("jugador-A", elementoDeB.id(), "Daga Robada"));
        assertEquals("Daga Corta", repositorio.buscarPorPropietario("jugador-B")
                .orElseThrow().elementos().getFirst().nombrePropio());
    }

    @Test
    @DisplayName("una operacion sin identidad autenticada se rechaza")
    void identidadRequerida() {
        assertThrows(IdentidadRequeridaException.class,
                () -> gestion.crear(null, "producto-1", TipoElementoInventario.ITEM, "Daga"));
    }

    @Test
    @DisplayName("modificar un elemento inexistente se rechaza")
    void elementoInexistente() {
        assertThrows(ElementoNoEncontradoException.class,
                () -> gestion.modificarNombre("jugador-A", "elemento-inexistente", "Daga"));
    }
}
