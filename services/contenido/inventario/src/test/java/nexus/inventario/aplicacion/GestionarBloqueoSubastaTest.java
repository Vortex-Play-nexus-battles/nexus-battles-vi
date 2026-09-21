package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoDisponibleException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GestionarBloqueoSubastaTest {

    private static final UUID PROPIETARIO_UID =
            UUID.fromString("ae8df97e-9ab9-4af5-bd2a-25715919e5f1");
    private static final UUID SUBASTA_UNO =
            UUID.fromString("89d9040d-52e0-44ae-8d8c-8ec033978afb");
    private static final UUID SUBASTA_DOS =
            UUID.fromString("51326b9d-1aa2-4d8a-bb7d-d3ad593f902d");

    private RepositorioInventariosEnMemoria repositorio;
    private GestionarBloqueoSubasta gestion;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        gestion = new GestionarBloqueoSubasta(repositorio);
        repositorio.guardar(Inventario.vacio(PROPIETARIO_UID.toString()).agregar(new ElementoInventario(
                "elemento-1", "producto-1", TipoElementoInventario.ITEM, "Amuleto")));
    }

    @Test
    @DisplayName("bloquea el elemento del propietario y persiste la subasta")
    void bloquearElementoPropio() {
        ElementoInventario bloqueado = gestion.bloquear(
                PROPIETARIO_UID, "elemento-1", SUBASTA_UNO, "operacion-1");

        assertFalse(bloqueado.disponible());
        assertEquals(SUBASTA_UNO.toString(), repositorio.buscarPorElementoId("elemento-1")
                .orElseThrow().elemento("elemento-1").subastaId());
    }

    @Test
    @DisplayName("rechaza bloquear un elemento de otro jugador")
    void rechazarElementoAjeno() {
        assertThrows(InventarioAjenoException.class, () -> gestion.bloquear(
                UUID.randomUUID(), "elemento-1", SUBASTA_UNO, "operacion-1"));
    }

    @Test
    @DisplayName("otra subasta no puede tomar un producto que ya esta bloqueado")
    void rechazarOtraSubasta() {
        gestion.bloquear(PROPIETARIO_UID, "elemento-1", SUBASTA_UNO, "operacion-1");

        assertThrows(ElementoNoDisponibleException.class, () -> gestion.bloquear(
                PROPIETARIO_UID, "elemento-1", SUBASTA_DOS, "operacion-2"));
    }

    @Test
    @DisplayName("el aviso de cierre libera y persiste el producto de forma idempotente")
    void liberarAlRecibirAvisoDeCierre() {
        gestion.bloquear(PROPIETARIO_UID, "elemento-1", SUBASTA_UNO, "publicar-1");

        ElementoInventario liberado = gestion.liberar(
                "elemento-1", SUBASTA_UNO, "cerrar-1");
        ElementoInventario repetido = gestion.liberar(
                "elemento-1", SUBASTA_UNO, "cerrar-1");

        assertTrue(liberado.disponible());
        assertTrue(repetido.disponible());
        assertTrue(repositorio.buscarPorElementoId("elemento-1")
                .orElseThrow().elemento("elemento-1").disponible());
    }

    @Test
    @DisplayName("el aviso de una subasta diferente conserva el bloqueo")
    void conservarBloqueoAnteAvisoAjeno() {
        gestion.bloquear(PROPIETARIO_UID, "elemento-1", SUBASTA_UNO, "publicar-1");

        assertThrows(ElementoNoDisponibleException.class, () -> gestion.liberar(
                "elemento-1", SUBASTA_DOS, "cerrar-2"));
        assertFalse(repositorio.buscarPorElementoId("elemento-1")
                .orElseThrow().elemento("elemento-1").disponible());
    }
}
