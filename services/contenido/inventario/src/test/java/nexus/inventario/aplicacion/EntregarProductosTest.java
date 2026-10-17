package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import nexus.inventario.aplicacion.EntregarProductos.ResultadoEntrega;
import nexus.inventario.dominio.ConflictoDeEscrituraException;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Entrega;
import nexus.inventario.dominio.EstadoEntrega;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.LineaDeEntrega;
import nexus.inventario.dominio.OrigenDeEntrega;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * B4 — {@code POST /api/v1/inventario/entregas}: la propiedad llega por un
 * canal valido, una sola vez por clave, todo o nada.
 */
class EntregarProductosTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T15:00:00Z");
    private static final UUID JUGADOR = UUID.fromString("7b6f3c1e-2f4d-4c55-9a8e-0d1f2e3a4b5c");
    private static final String CLAVE = "orden-1001-entrega";

    private RepositorioDeEntregasEnMemoria entregas;
    private RepositorioInventariosEnMemoria inventarios;
    private CatalogoDeProductosEnMemoria catalogo;
    private EntregarProductos servicio;

    @BeforeEach
    void preparar() {
        entregas = new RepositorioDeEntregasEnMemoria();
        inventarios = new RepositorioInventariosEnMemoria();
        catalogo = new CatalogoDeProductosEnMemoria()
                .registrar("espada", new ResolutorDeProducto.DetalleProducto(
                        "Espada de una mano", "ARMA", null, "ACTIVO"))
                .registrar("guerrero", new ResolutorDeProducto.DetalleProducto(
                        "Guerrero Tanque", "HEROE", "Guerrero Tanque", "ACTIVO"))
                .registrar("pocion", new ResolutorDeProducto.DetalleProducto(
                        "Pocion", "ITEM", null, "UNICO"))
                .registrarArmadura("peto", ParteArmadura.PECHO)
                .registrar("retirado", new ResolutorDeProducto.DetalleProducto(
                        "Retirado", "ITEM", null, "SUSPENDIDO"))
                .registrar("epica-defensa", new ResolutorDeProducto.DetalleProducto(
                        "Golpe de defensa", "EPICA", "Guerrero Tanque", "ACTIVO"))
                .registrar("epica-impulso", new ResolutorDeProducto.DetalleProducto(
                        "Segundo impulso", "EPICA", "Guerrero Armas", "ACTIVO"));
        servicio = new EntregarProductos(entregas, inventarios, catalogo, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static SolicitudDeEntrega compra(LineaDeEntrega... lineas) {
        return new SolicitudDeEntrega(JUGADOR, OrigenDeEntrega.COMPRA, "orden-1001", List.of(lineas));
    }

    private static LineaDeEntrega linea(String productoId, int cantidad) {
        return new LineaDeEntrega(productoId, cantidad);
    }

    private Inventario inventario() {
        return inventarios.buscarPorPropietario(JUGADOR.toString()).orElseThrow();
    }

    @Nested
    @DisplayName("una entrega nueva")
    class Nueva {

        @Test
        @DisplayName("da los elementos que dice el catalogo, con su origen y su referencia, y la anota en el inventario")
        void entregaLoQueDiceElCatalogo() {
            ResultadoEntrega resultado = servicio.entregar(
                    compra(linea("espada", 2), linea("peto", 1), linea("guerrero", 1)), CLAVE, "ms-ecommerce");

            assertTrue(resultado.realizadaAhora());
            Entrega entrega = resultado.entrega();
            assertEquals(EstadoEntrega.COMPLETADA, entrega.estado());
            assertEquals(AHORA, entrega.entregadaEn());
            assertEquals(JUGADOR.toString(), entrega.uid());
            assertEquals("ms-ecommerce", entrega.solicitante());
            assertEquals(4, entrega.elementos().size());

            ElementoInventario espada = entrega.elementos().get(0);
            assertEquals(TipoElementoInventario.ARMA, espada.tipo());
            assertEquals("Espada de una mano", espada.nombrePropio());
            assertEquals(OrigenDeEntrega.COMPRA, espada.origen());
            assertEquals("orden-1001", espada.referencia());
            assertNotEquals(espada.id(), entrega.elementos().get(1).id(), "cada unidad es un elemento propio");

            ElementoInventario peto = entrega.elementos().get(2);
            assertEquals(ParteArmadura.PECHO, peto.parteArmadura(), "la parte la decide el catalogo");

            ElementoInventario heroe = entrega.elementos().get(3);
            assertEquals(TipoElementoInventario.HEROE, heroe.tipo());
            assertEquals(1, heroe.nivel());
            assertEquals(0d, heroe.experiencia());

            Inventario inventario = inventario();
            assertEquals(entrega.elementos(), inventario.elementos());
            assertTrue(inventario.recibio(entrega.id()));
            assertEquals(EstadoEntrega.COMPLETADA, entregas.buscarPorClave(CLAVE).orElseThrow().estado());
        }

        @Test
        @DisplayName("se suma a lo que el jugador ya tenia")
        void seSumaALoQueTenia() {
            inventarios.guardar(Inventario.vacio(JUGADOR.toString()).agregar(
                    new ElementoInventario("previo", "espada", TipoElementoInventario.ARMA, "Mi espada")));

            servicio.entregar(compra(linea("pocion", 1)), CLAVE, "ms-ecommerce");

            List<ElementoInventario> elementos = inventario().elementos();
            assertEquals(2, elementos.size());
            assertEquals("previo", elementos.get(0).id());
            assertNull(elementos.get(0).origen(), "lo anterior no gana un origen que no tuvo");
            assertEquals("pocion", elementos.get(1).productoId());
            assertEquals(OrigenDeEntrega.COMPRA, elementos.get(1).origen());
        }

        @Test
        @DisplayName("un producto UNICO se entrega: el tiraje ya lo reservo quien vende")
        void unProductoUnicoSeEntrega() {
            assertEquals(1, servicio.entregar(compra(linea("pocion", 1)), CLAVE, "ms-ecommerce")
                    .entrega().elementos().size());
        }

        @Test
        @DisplayName("con dos lineas del mismo producto consulta el catalogo una sola vez")
        void consultaCadaProductoUnaVez() {
            servicio.entregar(compra(linea("espada", 1), linea("espada", 3)), CLAVE, "ms-ecommerce");

            assertEquals(1, catalogo.consultas());
            assertEquals(4, inventario().elementos().size());
        }
    }

    private static SolicitudDeEntrega deMision(LineaDeEntrega... lineas) {
        return new SolicitudDeEntrega(JUGADOR, OrigenDeEntrega.MISION, "mision-9", List.of(lineas));
    }

    @Nested
    @DisplayName("una epica que el jugador ya tiene")
    class EpicaQueYaSeTiene {

        private ElementoInventario epicaDelJugador(String id, String productoId) {
            return new ElementoInventario(id, productoId, TipoElementoInventario.EPICA, "Golpe de defensa");
        }

        @Test
        @DisplayName("la primera vez se crea, y la entrega no dice que ya la tuviera")
        void laPrimeraVezSeCrea() {
            ResultadoEntrega resultado = servicio.entregar(deMision(linea("epica-defensa", 1)), CLAVE, "misiones");

            assertEquals(1, resultado.entrega().elementos().size());
            assertEquals(TipoElementoInventario.EPICA, resultado.entrega().elementos().getFirst().tipo());
            assertTrue(resultado.entrega().yaTenia().isEmpty());
            assertEquals(1, inventario().elementos().size());
        }

        @Test
        @DisplayName("si ya la tiene no se crea otra copia: la entrega se completa y dice que ya la tenia")
        void siYaLaTieneNoSeCreaOtra() {
            inventarios.guardar(Inventario.vacio(JUGADOR.toString())
                    .agregar(epicaDelJugador("la-que-ya-tenia", "epica-defensa")));

            ResultadoEntrega resultado = servicio.entregar(deMision(linea("epica-defensa", 1)), CLAVE, "misiones");

            assertTrue(resultado.realizadaAhora());
            assertEquals(EstadoEntrega.COMPLETADA, resultado.entrega().estado());
            assertTrue(resultado.entrega().elementos().isEmpty());
            assertEquals(List.of("epica-defensa"), resultado.entrega().yaTenia());
            assertEquals(List.of("la-que-ya-tenia"),
                    inventario().elementos().stream().map(ElementoInventario::id).toList());
            assertTrue(inventario().recibio(resultado.entrega().id()), "la entrega queda anotada igual");
        }

        @Test
        @DisplayName("derrotar al mismo Master en otra ejecucion no da otra copia")
        void otraEjecucionNoDaOtraCopia() {
            servicio.entregar(deMision(linea("epica-defensa", 1)), "mision-1-epica", "misiones");

            ResultadoEntrega segunda = servicio.entregar(
                    new SolicitudDeEntrega(JUGADOR, OrigenDeEntrega.MISION, "mision-2",
                            List.of(linea("epica-defensa", 1))), "mision-2-epica", "misiones");

            assertTrue(segunda.entrega().elementos().isEmpty());
            assertEquals(List.of("epica-defensa"), segunda.entrega().yaTenia());
            assertEquals(1, inventario().elementos().size());
        }

        @Test
        @DisplayName("pedir dos copias de una epica que no tiene da una sola")
        void dosCopiasDanUna() {
            ResultadoEntrega resultado = servicio.entregar(deMision(linea("epica-defensa", 2)), CLAVE, "misiones");

            assertEquals(1, resultado.entrega().elementos().size());
            assertEquals(List.of("epica-defensa"), resultado.entrega().yaTenia(),
                    "la segunda copia ya no se da: se dice que sobra");
        }

        @Test
        @DisplayName("una epica repetida en dos lineas de la misma entrega tampoco se duplica")
        void dosLineasDeLaMismaEpica() {
            ResultadoEntrega resultado = servicio.entregar(
                    deMision(linea("epica-defensa", 1), linea("epica-defensa", 1)), CLAVE, "misiones");

            assertEquals(1, resultado.entrega().elementos().size());
            assertEquals(1, inventario().elementos().size());
        }

        @Test
        @DisplayName("lo demas de la misma entrega se entrega, y solo la epica repetida se omite")
        void loDemasSeEntrega() {
            inventarios.guardar(Inventario.vacio(JUGADOR.toString())
                    .agregar(epicaDelJugador("la-que-ya-tenia", "epica-defensa")));

            ResultadoEntrega resultado = servicio.entregar(
                    deMision(linea("epica-defensa", 1), linea("epica-impulso", 1), linea("espada", 1)),
                    CLAVE, "misiones");

            assertEquals(List.of("epica-impulso", "espada"),
                    resultado.entrega().elementos().stream().map(ElementoInventario::productoId).toList());
            assertEquals(List.of("epica-defensa"), resultado.entrega().yaTenia());
        }

        @Test
        @DisplayName("la epica de otro jugador no cuenta: cada uno tiene la suya")
        void laDeOtroJugadorNoCuenta() {
            String otro = UUID.randomUUID().toString();
            inventarios.guardar(Inventario.vacio(otro).agregar(epicaDelJugador("la-del-otro", "epica-defensa")));

            ResultadoEntrega resultado = servicio.entregar(deMision(linea("epica-defensa", 1)), CLAVE, "misiones");

            assertEquals(1, resultado.entrega().elementos().size());
            assertTrue(resultado.entrega().yaTenia().isEmpty());
        }

        @Test
        @DisplayName("solo las epicas: un objeto o un heroe repetido se entrega otra vez")
        void soloLasEpicas() {
            servicio.entregar(compra(linea("pocion", 1), linea("guerrero", 1)), "orden-1", "ms-ecommerce");

            ResultadoEntrega otraVez = servicio.entregar(
                    compra(linea("pocion", 1), linea("guerrero", 1)), "orden-2", "ms-ecommerce");

            assertEquals(2, otraVez.entrega().elementos().size());
            assertTrue(otraVez.entrega().yaTenia().isEmpty());
            assertEquals(4, inventario().elementos().size());
        }

        @Test
        @DisplayName("repetir la clave devuelve la misma respuesta, tambien lo que ya tenia")
        void repetirLaClaveDevuelveLoMismo() {
            inventarios.guardar(Inventario.vacio(JUGADOR.toString())
                    .agregar(epicaDelJugador("la-que-ya-tenia", "epica-defensa")));
            ResultadoEntrega primera = servicio.entregar(deMision(linea("epica-defensa", 1)), CLAVE, "misiones");

            ResultadoEntrega repetida = servicio.entregar(deMision(linea("epica-defensa", 1)), CLAVE, "misiones");

            assertFalse(repetida.realizadaAhora());
            assertEquals(primera.entrega(), repetida.entrega());
            assertEquals(List.of("epica-defensa"), repetida.entrega().yaTenia());
            assertEquals(1, inventario().elementos().size());
        }
    }

    @Nested
    @DisplayName("la misma clave")
    class MismaClave {

        @Test
        @DisplayName("con el mismo cuerpo devuelve la entrega original (200) y no duplica nada")
        void mismoCuerpoDevuelveLaOriginal() {
            ResultadoEntrega primera = servicio.entregar(compra(linea("espada", 2)), CLAVE, "ms-ecommerce");
            int consultasTrasLaPrimera = catalogo.consultas();

            ResultadoEntrega repetida = servicio.entregar(compra(linea("espada", 2)), CLAVE, "ms-ecommerce");

            assertFalse(repetida.realizadaAhora());
            assertEquals(primera.entrega(), repetida.entrega());
            assertEquals(2, inventario().elementos().size());
            assertEquals(consultasTrasLaPrimera, catalogo.consultas(), "la repeticion no vuelve a preguntar");
            assertEquals(1, entregas.cantidad());
        }

        @Test
        @DisplayName("el orden de las lineas no cuenta: es la misma entrega")
        void elOrdenNoCuenta() {
            ResultadoEntrega primera = servicio.entregar(
                    compra(linea("espada", 1), linea("pocion", 1)), CLAVE, "ms-ecommerce");

            ResultadoEntrega repetida = servicio.entregar(
                    compra(linea("pocion", 1), linea("espada", 1)), CLAVE, "ms-ecommerce");

            assertEquals(primera.entrega().id(), repetida.entrega().id());
            assertFalse(repetida.realizadaAhora());
        }

        @Test
        @DisplayName("con otro cuerpo es 409 y no cambia nada")
        void otroCuerpoEsConflicto() {
            servicio.entregar(compra(linea("espada", 1)), CLAVE, "ms-ecommerce");

            assertThrows(ClaveDeEntregaReutilizadaException.class,
                    () -> servicio.entregar(compra(linea("espada", 2)), CLAVE, "ms-ecommerce"));
            assertThrows(ClaveDeEntregaReutilizadaException.class, () -> servicio.entregar(
                    new SolicitudDeEntrega(UUID.randomUUID(), OrigenDeEntrega.COMPRA, "orden-1001",
                            List.of(linea("espada", 1))), CLAVE, "ms-ecommerce"));
            assertThrows(ClaveDeEntregaReutilizadaException.class, () -> servicio.entregar(
                    new SolicitudDeEntrega(JUGADOR, OrigenDeEntrega.COFRE, "orden-1001",
                            List.of(linea("espada", 1))), CLAVE, "ms-ecommerce"));

            assertEquals(1, inventario().elementos().size());
        }

        @Test
        @DisplayName("si otra peticion con la misma clave la registro primero, se sigue con la suya")
        void otraPeticionRegistroPrimero() {
            SolicitudDeEntrega solicitud = compra(linea("espada", 1));
            Entrega registradaPorOtra = Entrega.pendiente(
                    "entrega-de-la-otra", CLAVE, solicitud.huella(), JUGADOR.toString(), OrigenDeEntrega.COMPRA,
                    "orden-1001", solicitud.productos(),
                    List.of(ElementoInventario.entregado("elemento-de-la-otra", "espada", TipoElementoInventario.ARMA,
                            "Espada de una mano", null, OrigenDeEntrega.COMPRA, "orden-1001")),
                    "ms-ecommerce", AHORA);
            entregas.antesDeRegistrar(() -> entregas.registrar(registradaPorOtra));

            ResultadoEntrega resultado = servicio.entregar(solicitud, CLAVE, "ms-ecommerce");

            assertEquals("entrega-de-la-otra", resultado.entrega().id());
            assertEquals(List.of("elemento-de-la-otra"),
                    inventario().elementos().stream().map(ElementoInventario::id).toList());
        }

        @Test
        @DisplayName("si otra peticion registro la misma clave con otro cuerpo, es 409")
        void otraPeticionConOtroCuerpo() {
            SolicitudDeEntrega otra = compra(linea("pocion", 1));
            entregas.antesDeRegistrar(() -> entregas.registrar(Entrega.pendiente(
                    "entrega-de-la-otra", CLAVE, otra.huella(), JUGADOR.toString(), OrigenDeEntrega.COMPRA,
                    "orden-1001", otra.productos(), List.of(), "ms-ecommerce", AHORA)));

            assertThrows(ClaveDeEntregaReutilizadaException.class,
                    () -> servicio.entregar(compra(linea("espada", 1)), CLAVE, "ms-ecommerce"));
        }
    }

    @Nested
    @DisplayName("todo o nada")
    class TodoONada {

        @Test
        @DisplayName("un producto suspendido rechaza la entrega entera (409) y no se guarda nada")
        void productoSuspendido() {
            assertThrows(ProductoSuspendidoException.class, () -> servicio.entregar(
                    compra(linea("espada", 1), linea("retirado", 1)), CLAVE, "ms-ecommerce"));

            assertNothingSaved();
        }

        @Test
        @DisplayName("un producto que no existe rechaza la entrega entera (422) y no se guarda nada")
        void productoInexistente() {
            assertThrows(ProductoInexistenteException.class, () -> servicio.entregar(
                    compra(linea("espada", 1), linea("espada-corta", 1)), CLAVE, "ms-ecommerce"));

            assertNothingSaved();
        }

        @Test
        @DisplayName("un tipo que el inventario no conoce no es un producto entregable (422)")
        void tipoDesconocido() {
            catalogo.registrar("cofre", new ResolutorDeProducto.DetalleProducto("Cofre", "COFRE", null, "ACTIVO"));

            assertThrows(ProductoInexistenteException.class,
                    () -> servicio.entregar(compra(linea("cofre", 1)), CLAVE, "ms-ecommerce"));
            assertNothingSaved();
        }

        @Test
        @DisplayName("una respuesta sin tipo no es un producto (422)")
        void sinTipo() {
            catalogo.registrar("raro", new ResolutorDeProducto.DetalleProducto(null, null, null, null));

            assertThrows(ProductoInexistenteException.class,
                    () -> servicio.entregar(compra(linea("raro", 1)), CLAVE, "ms-ecommerce"));
        }

        @Test
        @DisplayName("una armadura sin parte en el catalogo no tiene ranura: 422 y nada guardado")
        void armaduraSinParte() {
            catalogo.registrarArmadura("armadura-sin-parte", (ParteArmadura) null);

            assertThrows(ProductoIncompletoException.class,
                    () -> servicio.entregar(compra(linea("armadura-sin-parte", 1)), CLAVE, "ms-ecommerce"));
            assertNothingSaved();
        }

        @Test
        @DisplayName("sin catalogo no se entrega a ciegas: 503 y nada guardado")
        void catalogoCaido() {
            catalogo.caer();

            assertThrows(CatalogoNoDisponibleException.class,
                    () -> servicio.entregar(compra(linea("espada", 1)), CLAVE, "ms-ecommerce"));
            assertNothingSaved();
        }

        @Test
        @DisplayName("un producto sin nombre en el catalogo se nombra por su id")
        void sinNombre() {
            catalogo.registrar("anonimo", new ResolutorDeProducto.DetalleProducto(" ", "ITEM", null, "ACTIVO"));

            assertEquals("anonimo", servicio.entregar(compra(linea("anonimo", 1)), CLAVE, "ms-ecommerce")
                    .entrega().elementos().getFirst().nombrePropio());
        }

        private void assertNothingSaved() {
            assertEquals(0, entregas.cantidad());
            assertTrue(inventarios.buscarPorPropietario(JUGADOR.toString()).isEmpty());
        }
    }

    @Nested
    @DisplayName("un fallo a medias")
    class FalloAMedias {

        @Test
        @DisplayName("si cae despues de escribir el inventario, el reintento la completa sin duplicar")
        void caeAntesDeCompletar() {
            entregas.fallarAlCompletar();
            SolicitudDeEntrega solicitud = compra(linea("espada", 2));

            assertThrows(FalloPersistenciaInventarioException.class,
                    () -> servicio.entregar(solicitud, CLAVE, "ms-ecommerce"));
            assertEquals(EstadoEntrega.PENDIENTE, entregas.buscarPorClave(CLAVE).orElseThrow().estado());
            List<String> recibidos = inventario().elementos().stream().map(ElementoInventario::id).toList();
            assertEquals(2, recibidos.size());

            ResultadoEntrega reintento = servicio.entregar(solicitud, CLAVE, "ms-ecommerce");

            assertTrue(reintento.realizadaAhora());
            assertEquals(EstadoEntrega.COMPLETADA, reintento.entrega().estado());
            assertEquals(recibidos, reintento.entrega().elementos().stream().map(ElementoInventario::id).toList());
            assertEquals(recibidos, inventario().elementos().stream().map(ElementoInventario::id).toList());
        }

        @Test
        @DisplayName("si cae antes de escribir el inventario, el reintento aplica los MISMOS elementos una vez")
        void caeAlEscribirElInventario() {
            inventarios.fallarSiguienteGuardado();
            SolicitudDeEntrega solicitud = compra(linea("peto", 1));

            assertThrows(FalloPersistenciaInventarioException.class,
                    () -> servicio.entregar(solicitud, CLAVE, "ms-ecommerce"));
            Entrega pendiente = entregas.buscarPorClave(CLAVE).orElseThrow();
            assertEquals(EstadoEntrega.PENDIENTE, pendiente.estado());
            assertTrue(inventarios.buscarPorPropietario(JUGADOR.toString()).isEmpty());

            catalogo.caer(); // el reintento ya no necesita el catalogo: los elementos estan planeados
            ResultadoEntrega reintento = servicio.entregar(solicitud, CLAVE, "ms-ecommerce");

            assertEquals(pendiente.elementos(), inventario().elementos());
            assertEquals(pendiente.id(), reintento.entrega().id());
        }

        @Test
        @DisplayName("si el jugador ya movio lo recibido, el reintento no lo vuelve a dar")
        void noReaplicaLoQueElJugadorYaMovio() {
            entregas.fallarAlCompletar();
            SolicitudDeEntrega solicitud = compra(linea("espada", 1));
            assertThrows(FalloPersistenciaInventarioException.class,
                    () -> servicio.entregar(solicitud, CLAVE, "ms-ecommerce"));
            String recibido = inventario().elementos().getFirst().id();
            inventarios.guardar(inventario().eliminarElemento(recibido));

            servicio.entregar(solicitud, CLAVE, "ms-ecommerce");

            assertEquals(List.of(), inventario().elementos());
            assertEquals(EstadoEntrega.COMPLETADA, entregas.buscarPorClave(CLAVE).orElseThrow().estado());
        }

        @Test
        @DisplayName("si otra escritura llego antes al inventario, relee y reintenta")
        void conflictoSeReintenta() {
            inventarios.conflictoEnLosSiguientesGuardados(EntregarProductos.INTENTOS_ANTE_CONFLICTO - 1);

            ResultadoEntrega resultado = servicio.entregar(compra(linea("espada", 1)), CLAVE, "ms-ecommerce");

            assertTrue(resultado.realizadaAhora());
            assertEquals(1, inventario().elementos().size());
        }

        @Test
        @DisplayName("si los conflictos no ceden, 503 con la entrega PENDIENTE; el reintento la termina")
        void conflictosQueNoCeden() {
            inventarios.conflictoEnLosSiguientesGuardados(EntregarProductos.INTENTOS_ANTE_CONFLICTO);
            SolicitudDeEntrega solicitud = compra(linea("espada", 1));

            assertThrows(ConflictoDeEscrituraException.class,
                    () -> servicio.entregar(solicitud, CLAVE, "ms-ecommerce"));
            assertEquals(EstadoEntrega.PENDIENTE, entregas.buscarPorClave(CLAVE).orElseThrow().estado());

            servicio.entregar(solicitud, CLAVE, "ms-ecommerce");

            assertEquals(1, inventario().elementos().size());
            assertEquals(EstadoEntrega.COMPLETADA, entregas.buscarPorClave(CLAVE).orElseThrow().estado());
        }
    }

    @Test
    @DisplayName("la huella resume uid, origen, referencia y productos; no el orden de las lineas")
    void huella() {
        String base = compra(linea("a", 1), linea("b", 2)).huella();

        assertEquals(base, compra(linea("b", 2), linea("a", 1)).huella());
        assertEquals(64, base.length());
        assertNotEquals(base, compra(linea("a", 1), linea("b", 3)).huella());
        assertNotEquals(base, new SolicitudDeEntrega(JUGADOR, OrigenDeEntrega.COMPRA, "orden-1002",
                List.of(linea("a", 1), linea("b", 2))).huella());
    }
}
