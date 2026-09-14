package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoDisponibleException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GestionarBloqueoSubastaTest {

    private RepositorioInventariosEnMemoria repositorio;
    private GestionarBloqueoSubasta gestion;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        gestion = new GestionarBloqueoSubasta(repositorio);
        repositorio.guardar(Inventario.vacio("jugador-A").agregar(new ElementoInventario(
                "elemento-1", "producto-1", TipoElementoInventario.ITEM, "Amuleto")));
    }

    @Test
    @DisplayName("bloquea el elemento del propietario y persiste la subasta")
    void bloquearElementoPropio() {
        ElementoInventario bloqueado = gestion.bloquear(
                "jugador-A", "elemento-1", "subasta-1", "operacion-1");

        assertFalse(bloqueado.disponible());
        assertEquals("subasta-1", repositorio.buscarPorElementoId("elemento-1")
                .orElseThrow().elemento("elemento-1").subastaId());
    }

    @Test
    @DisplayName("rechaza bloquear un elemento de otro jugador")
    void rechazarElementoAjeno() {
        assertThrows(InventarioAjenoException.class, () -> gestion.bloquear(
                "jugador-B", "elemento-1", "subasta-1", "operacion-1"));
    }

    @Test
    @DisplayName("otra subasta no puede tomar un producto que ya esta bloqueado")
    void rechazarOtraSubasta() {
        gestion.bloquear("jugador-A", "elemento-1", "subasta-1", "operacion-1");

        assertThrows(ElementoNoDisponibleException.class, () -> gestion.bloquear(
                "jugador-A", "elemento-1", "subasta-2", "operacion-2"));
    }

    @Test
    @DisplayName("el aviso de cierre libera y persiste el producto de forma idempotente")
    void liberarAlRecibirAvisoDeCierre() {
        gestion.bloquear("jugador-A", "elemento-1", "subasta-1", "publicar-1");

        ElementoInventario liberado = gestion.liberar(
                "elemento-1", "subasta-1", "cerrar-1");
        ElementoInventario repetido = gestion.liberar(
                "elemento-1", "subasta-1", "cerrar-1");

        assertTrue(liberado.disponible());
        assertTrue(repetido.disponible());
        assertTrue(repositorio.buscarPorElementoId("elemento-1")
                .orElseThrow().elemento("elemento-1").disponible());
    }

    @Test
    @DisplayName("el aviso de una subasta diferente conserva el bloqueo")
    void conservarBloqueoAnteAvisoAjeno() {
        gestion.bloquear("jugador-A", "elemento-1", "subasta-1", "publicar-1");

        assertThrows(ElementoNoDisponibleException.class, () -> gestion.liberar(
                "elemento-1", "subasta-2", "cerrar-2"));
        assertFalse(repositorio.buscarPorElementoId("elemento-1")
                .orElseThrow().elemento("elemento-1").disponible());
    }
}
