package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferirElementoPorSubastaTest {

    private static final UUID VENDEDOR = UUID.fromString("ae8df97e-9ab9-4af5-bd2a-25715919e5f1");
    private static final UUID COMPRADOR = UUID.fromString("7c1f0b2e-5d43-4a91-9f0c-1b2e3d4a5b6c");
    private static final UUID SUBASTA = UUID.fromString("89d9040d-52e0-44ae-8d8c-8ec033978afb");
    private static final UUID OTRA_SUBASTA = UUID.fromString("51326b9d-1aa2-4d8a-bb7d-d3ad593f902d");

    private RepositorioInventariosEnMemoria repositorio;
    private GestionarBloqueoSubasta bloqueos;
    private TransferirElementoPorSubasta transferencias;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        bloqueos = new GestionarBloqueoSubasta(repositorio);
        transferencias = new TransferirElementoPorSubasta(repositorio);
        repositorio.guardar(Inventario.vacio(VENDEDOR.toString()).agregar(new ElementoInventario(
                "elemento-1", "producto-1", TipoElementoInventario.ITEM, "Amuleto")));
        bloqueos.bloquear(VENDEDOR, "elemento-1", SUBASTA, "bloqueo-1");
    }

    @Test
    @DisplayName("el elemento sale del inventario del vendedor y entra en el del comprador")
    void transferirCambiaDeAgregado() {
        transferencias.transferir("elemento-1", COMPRADOR, SUBASTA, "cierre-1");

        Inventario delComprador = repositorio.buscarPorPropietario(COMPRADOR.toString()).orElseThrow();
        assertEquals(1, delComprador.elementos().size());
        assertEquals("elemento-1", delComprador.elementos().get(0).id());

        // Y deja de estar en el del vendedor: si siguiera en los dos, el mismo
        // objeto existiria dos veces.
        Inventario delVendedor = repositorio.buscarPorPropietario(VENDEDOR.toString()).orElseThrow();
        assertTrue(delVendedor.elementos().isEmpty());
    }

    @Test
    @DisplayName("el elemento llega libre: el bloqueo de la subasta se suelta en la misma operacion")
    void elElementoLlegaDisponible() {
        ElementoInventario transferido = transferencias.transferir(
                "elemento-1", COMPRADOR, SUBASTA, "cierre-1");

        // Si llegara bloqueado, el comprador tendria el objeto sin poder usarlo
        // y ms-subastas necesitaria una segunda llamada para soltarlo, con la
        // ventana de quedarse a medias entre las dos.
        assertTrue(transferido.disponible());
    }

    /**
     * El cierre por vencimiento de ms-subastas corre dentro de una transaccion
     * y reintenta la misma subasta cada 30 s. Repetir la llamada tiene que ser
     * inofensivo, o la segunda pasada vuelve a mover el producto.
     */
    @Test
    @DisplayName("repetir la transferencia termina bien y no mueve nada")
    void transferirDosVecesEsInofensivo() {
        transferencias.transferir("elemento-1", COMPRADOR, SUBASTA, "cierre-1");
        ElementoInventario segunda = transferencias.transferir(
                "elemento-1", COMPRADOR, SUBASTA, "cierre-1");

        assertEquals("elemento-1", segunda.id());
        assertEquals(1, repositorio.buscarPorPropietario(COMPRADOR.toString())
                .orElseThrow().elementos().size());
    }

    /**
     * Sin esta comprobacion, cualquier servicio con credencial podria cambiar
     * de dueno un elemento ajeno inventandose un identificador de subasta.
     */
    @Test
    @DisplayName("no se transfiere un elemento bloqueado por OTRA subasta")
    void exigeQueElBloqueoSeaDeEsaSubasta() {
        assertThrows(TransferenciaSinBloqueoException.class,
                () -> transferencias.transferir("elemento-1", COMPRADOR, OTRA_SUBASTA, "cierre-1"));

        assertEquals(VENDEDOR.toString(),
                repositorio.buscarPorElementoId("elemento-1").orElseThrow().propietarioId());
    }

    @Test
    @DisplayName("no se transfiere un elemento que no esta en ninguna subasta")
    void exigeQueEsteBloqueado() {
        repositorio.guardar(repositorio.buscarPorPropietario(VENDEDOR.toString()).orElseThrow()
                .liberarBloqueoSubasta("elemento-1", SUBASTA.toString()));

        assertThrows(TransferenciaSinBloqueoException.class,
                () -> transferencias.transferir("elemento-1", COMPRADOR, SUBASTA, "cierre-1"));
    }

    @Test
    @DisplayName("un elemento que ya no existe se distingue de uno mal bloqueado")
    void elementoInexistente() {
        assertThrows(ElementoNoEncontradoException.class,
                () -> transferencias.transferir("no-existe", COMPRADOR, SUBASTA, "cierre-1"));
    }

    @Test
    @DisplayName("exige el nuevo propietario y la subasta antes de tocar nada")
    void exigeLosDatosObligatorios() {
        assertThrows(NullPointerException.class,
                () -> transferencias.transferir("elemento-1", null, SUBASTA, "cierre-1"));
        assertThrows(NullPointerException.class,
                () -> transferencias.transferir("elemento-1", COMPRADOR, null, "cierre-1"));
        assertThrows(IllegalArgumentException.class,
                () -> transferencias.transferir("  ", COMPRADOR, SUBASTA, "cierre-1"));

        assertFalse(repositorio.buscarPorElementoId("elemento-1").orElseThrow()
                .elemento("elemento-1").disponible());
    }
}
