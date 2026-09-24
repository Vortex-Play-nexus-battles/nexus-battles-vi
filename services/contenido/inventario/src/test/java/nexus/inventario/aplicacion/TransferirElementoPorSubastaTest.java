package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
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

    /**
     * Este caso afirmaba lo contrario: que el bloqueo se soltaba dentro de la
     * transferencia para ahorrarle a ms-subastas una segunda llamada. Se invierte
     * a proposito, y por dos razones que solo se ven mirando el orden real de
     * {@code MotorPujasService}.
     *
     * <p>Primera: ahi la transferencia ocurre ANTES del cobro
     * ({@code transferirProducto} y despues {@code creditoClient.consumir}). En
     * el instante en que este metodo termina, el ganador tiene el objeto y no lo
     * ha pagado todavia. Si llegara libre podria equiparlo o revenderlo en esa
     * ventana, y entonces la compensacion no encontraria que devolver.
     *
     * <p>Segunda: cuando el cobro falla, ms-subastas compensa devolviendo el
     * elemento al vendedor con esta misma operacion, que exige bloqueo de esa
     * subasta. Soltarlo aqui hacia que la compensacion muriera siempre con 409 —
     * estaba escrita y no podia funcionar nunca.
     *
     * <p>La segunda llamada no desaparece, se mueve: ms-subastas suelta el
     * bloqueo despues de confirmar la venta, y esa operacion es idempotente y no
     * mira quien es el dueno, asi que se puede reintentar sola.
     */
    @Test
    @DisplayName("el bloqueo viaja con el elemento: el ganador aun no ha pagado")
    void elBloqueoViajaConElElemento() {
        ElementoInventario transferido = transferencias.transferir(
                "elemento-1", COMPRADOR, SUBASTA, "cierre-1");

        assertFalse(transferido.disponible());
        assertEquals(SUBASTA.toString(), transferido.subastaId());
    }

    /**
     * El segundo paso del diseno de dos llamadas. Si esto no funcionara, el
     * ganador se quedaria con un objeto pagado que no puede usar: peor que el
     * problema que se arregla conservando el bloqueo.
     */
    @Test
    @DisplayName("soltar el bloqueo despues de la venta no exige ser el propietario original")
    void laVentaDefinitivaDejaElElementoUsable() {
        transferencias.transferir("elemento-1", COMPRADOR, SUBASTA, "cierre-1");

        ElementoInventario libre = bloqueos.liberar("elemento-1", SUBASTA, "liberar-cierre-1");

        assertTrue(libre.disponible());
        assertTrue(repositorio.buscarPorPropietario(COMPRADOR.toString()).orElseThrow()
                .elemento("elemento-1").disponible());
    }

    /**
     * La compensacion de {@code MotorPujasService} cuando el cobro falla despues
     * de haber transferido: la misma operacion, con el vendedor como destino.
     * Antes terminaba en 409 porque el elemento llegaba sin bloqueo.
     */
    @Test
    @DisplayName("se puede devolver el elemento al vendedor si el cobro falla")
    void laCompensacionDevuelveElElementoAlVendedor() {
        transferencias.transferir("elemento-1", COMPRADOR, SUBASTA, "cierre-1");

        transferencias.transferir("elemento-1", VENDEDOR, SUBASTA, "devolver-cierre-1");

        assertEquals(VENDEDOR.toString(),
                repositorio.buscarPorElementoId("elemento-1").orElseThrow().propietarioId());
        assertEquals(1, repositorio.buscarTodosPorElementoId("elemento-1").size());
        assertTrue(repositorio.buscarPorPropietario(COMPRADOR.toString()).orElseThrow()
                .elementos().isEmpty());
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
     * El inventario esta modelado por dueno y este servicio no tiene
     * transacciones de Mongo, asi que la transferencia son dos escrituras que
     * pueden quedarse a medias. Se escribe primero el destino, asi que la
     * interrupcion duplica el elemento en vez de perderlo — y el reintento tiene
     * que terminar el trabajo, no devolver «ya esta» y dejar la copia de sobra
     * para siempre.
     */
    @Test
    @DisplayName("el reintento repara una transferencia interrumpida a medias")
    void convergeDesdeElEstadoDuplicado() {
        repositorio.guardar(Inventario.vacio(COMPRADOR.toString()).agregar(new ElementoInventario(
                "elemento-1", "producto-1", TipoElementoInventario.ITEM, "Amuleto",
                null, SUBASTA.toString())));
        assertEquals(2, repositorio.buscarTodosPorElementoId("elemento-1").size());

        ElementoInventario reparado = transferencias.transferir(
                "elemento-1", COMPRADOR, SUBASTA, "cierre-1");

        assertEquals("elemento-1", reparado.id());
        assertEquals(1, repositorio.buscarTodosPorElementoId("elemento-1").size());
        assertEquals(COMPRADOR.toString(),
                repositorio.buscarPorElementoId("elemento-1").orElseThrow().propietarioId());
        assertTrue(repositorio.buscarPorPropietario(VENDEDOR.toString()).orElseThrow()
                .elementos().isEmpty());
    }

    /**
     * La razon por la que el destino se escribe primero: si la escritura que
     * falla es la primera, el elemento no se ha movido de sitio y el reintento
     * encuentra exactamente el estado inicial. Con el orden contrario, un fallo
     * en la segunda dejaba el elemento fuera de los dos inventarios y todos los
     * reintentos recibian 404.
     */
    @Test
    @DisplayName("si falla la primera escritura el elemento sigue entero con el vendedor")
    void unFalloAlPrincipioNoPierdeElElemento() {
        repositorio.fallarSiguienteGuardado();

        assertThrows(FalloPersistenciaInventarioException.class,
                () -> transferencias.transferir("elemento-1", COMPRADOR, SUBASTA, "cierre-1"));

        assertEquals(1, repositorio.buscarTodosPorElementoId("elemento-1").size());
        assertEquals(VENDEDOR.toString(),
                repositorio.buscarPorElementoId("elemento-1").orElseThrow().propietarioId());
        assertEquals(SUBASTA.toString(), repositorio.buscarPorElementoId("elemento-1")
                .orElseThrow().elemento("elemento-1").subastaId());
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
