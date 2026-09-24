package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GestionarInventarioTest {

    private RepositorioInventariosEnMemoria repositorio;
    private GestionarInventario gestion;
    private CatalogoDeProductosEnMemoria catalogo;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        catalogo = new CatalogoDeProductosEnMemoria()
                .registrar("producto-1", TipoElementoInventario.ITEM);
        gestion = new GestionarInventario(repositorio, catalogo);
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

    // --- Solo productos del catalogo (RG-074: los items los crea el disenador) ---

    @Test
    @DisplayName("un producto del catalogo se agrega con el tipo que manda el catalogo")
    void crearConProductoDelCatalogo() {
        catalogo.registrar("1647b2ea-096d-37e7-b580-0172e4c62313", TipoElementoInventario.ARMA);

        ElementoInventario creado = gestion.crear("jugador-A",
                "1647b2ea-096d-37e7-b580-0172e4c62313", TipoElementoInventario.ARMA, "Mi espada");

        assertEquals(TipoElementoInventario.ARMA, creado.tipo());
        assertEquals("1647b2ea-096d-37e7-b580-0172e4c62313", repositorio.buscarPorPropietario("jugador-A")
                .orElseThrow().elementos().getFirst().productoId());
    }

    @Test
    @DisplayName("un producto que no existe en el catalogo se rechaza y no se guarda nada")
    void rechazarProductoInexistente() {
        assertThrows(ProductoInexistenteException.class, () -> gestion.crear(
                "jugador-A", "espada-corta", TipoElementoInventario.ARMA, "Espada inventada"));

        assertTrue(repositorio.buscarPorPropietario("jugador-A").isEmpty());
    }

    @Test
    @DisplayName("un producto suspendido en el catalogo no se puede agregar")
    void rechazarProductoSuspendido() {
        catalogo.registrar("item-retirado", TipoElementoInventario.ITEM, "SUSPENDIDO");

        assertThrows(ProductoSuspendidoException.class, () -> gestion.crear(
                "jugador-A", "item-retirado", TipoElementoInventario.ITEM, "Reliquia"));

        assertTrue(repositorio.buscarPorPropietario("jugador-A").isEmpty());
    }

    @Test
    @DisplayName("un producto unico del catalogo si se puede agregar")
    void aceptarProductoUnico() {
        catalogo.registrar("epica-unica", TipoElementoInventario.EPICA, "UNICO");

        ElementoInventario creado = gestion.crear(
                "jugador-A", "epica-unica", TipoElementoInventario.EPICA, "Golpe");

        assertEquals(TipoElementoInventario.EPICA, creado.tipo());
    }

    @Test
    @DisplayName("si el tipo pedido no es el del producto, se rechaza")
    void rechazarTipoQueNoCoincide() {
        catalogo.registrar("aec4fbd2-9615-352a-9f2b-3fad781e123c", TipoElementoInventario.HEROE);

        assertThrows(TipoNoCoincideException.class, () -> gestion.crear(
                "jugador-A", "aec4fbd2-9615-352a-9f2b-3fad781e123c", TipoElementoInventario.ARMA, "Heroe disfrazado"));

        assertTrue(repositorio.buscarPorPropietario("jugador-A").isEmpty());
    }

    @Test
    @DisplayName("la armadura de catalogo sigue exigiendo su parte")
    void armaduraDeCatalogoConParte() {
        catalogo.registrar("fbce687b-7496-3526-aef0-500b3fe2135e", TipoElementoInventario.ARMADURA);

        ElementoInventario creado = gestion.crear("jugador-A",
                "fbce687b-7496-3526-aef0-500b3fe2135e", TipoElementoInventario.ARMADURA,
                "Peto", ParteArmadura.PECHO);

        assertEquals(ParteArmadura.PECHO, creado.parteArmadura());
    }

    @Test
    @DisplayName("si el servicio de productos no responde, no se acepta a ciegas")
    void rechazarSiProductosNoResponde() {
        catalogo.caer();

        assertThrows(CatalogoNoDisponibleException.class, () -> gestion.crear(
                "jugador-A", "producto-1", TipoElementoInventario.ITEM, "Amuleto"));

        assertTrue(repositorio.buscarPorPropietario("jugador-A").isEmpty());
    }

    @Test
    @DisplayName("una respuesta de productos sin tipo no es un producto: se trata como inexistente")
    void respuestaSinTipoEsProductoInexistente() {
        ResolutorDeProducto sinTipo = productoId ->
                new ResolutorDeProducto.DetalleProducto(null, null, null, null);
        GestionarInventario conRespuestaRara = new GestionarInventario(repositorio, sinTipo);

        assertThrows(ProductoInexistenteException.class, () -> conRespuestaRara.crear(
                "jugador-A", "estadisticas", TipoElementoInventario.ITEM, "Resumen"));
    }
}
