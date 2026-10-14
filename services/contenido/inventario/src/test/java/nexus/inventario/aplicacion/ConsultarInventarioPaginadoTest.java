package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HU-INV-001, criterio 1: la vitrina entrega dieciseis productos por pagina. */
class ConsultarInventarioPaginadoTest {

    private RepositorioInventariosEnMemoria repositorio;
    private ConsultarInventarioPaginado consulta;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        consulta = new ConsultarInventarioPaginado(repositorio);
    }

    private void conElementos(String propietario, int cantidad) {
        Inventario inventario = Inventario.vacio(propietario);
        for (int i = 0; i < cantidad; i++) {
            inventario = inventario.agregar(new ElementoInventario(
                    "elemento-" + i, "producto-" + i,
                    TipoElementoInventario.ARMA, "Espada " + i));
        }
        repositorio.guardar(inventario);
    }

    @Test
    @DisplayName("la primera pagina entrega exactamente dieciseis de cuarenta")
    void primeraPaginaCompleta() {
        conElementos("jugador-A", 40);

        var pagina = consulta.consultar("jugador-A", 0);

        assertEquals(16, pagina.elementos().size());
        assertEquals(40, pagina.totalElementos());
        assertEquals(3, pagina.totalPaginas());
        assertFalse(pagina.ultima());
    }

    @Test
    @DisplayName("la ultima pagina entrega solo el resto, sin rellenar")
    void ultimaPaginaParcial() {
        conElementos("jugador-A", 40);

        var pagina = consulta.consultar("jugador-A", 2);

        assertEquals(8, pagina.elementos().size());
        assertTrue(pagina.ultima());
    }

    @Test
    @DisplayName("un jugador sin inventario recibe una pagina vacia, no un error")
    void jugadorSinInventario() {
        var pagina = consulta.consultar("jugador-nuevo", 0);

        assertTrue(pagina.elementos().isEmpty());
        assertEquals(0, pagina.totalElementos());
        assertEquals(0, pagina.totalPaginas());
        assertTrue(pagina.ultima());
    }

    @Test
    @DisplayName("una pagina mas alla del final llega vacia y no revienta")
    void paginaFueraDeRango() {
        conElementos("jugador-A", 5);

        assertTrue(consulta.consultar("jugador-A", 9).elementos().isEmpty());
    }

    @Test
    @DisplayName("se conserva el orden en que los elementos entraron al inventario")
    void conservaElOrden() {
        conElementos("jugador-A", 3);

        var nombres = consulta.consultar("jugador-A", 0).elementos().stream()
                .map(ElementoInventario::nombrePropio).toList();

        assertEquals(IntStream.range(0, 3).mapToObj(i -> "Espada " + i).toList(), nombres);
    }

    @Test
    @DisplayName("sin aviso de subastas conserva el bloqueo persistido por defecto")
    void conservaBloqueoSiSubastasNoResponde() {
        Inventario bloqueado = Inventario.vacio("jugador-A")
                .agregar(new ElementoInventario(
                        "elemento-1", "producto-1",
                        TipoElementoInventario.ITEM, "Reliquia"))
                .bloquearEnSubasta("elemento-1", "subasta-1");
        repositorio.guardar(bloqueado);

        ElementoInventario primeraEvaluacion = consulta.consultar("jugador-A", 0)
                .elementos().get(0);
        ElementoInventario segundaEvaluacion = consulta.consultar("jugador-A", 0)
                .elementos().get(0);

        assertFalse(primeraEvaluacion.disponible());
        assertFalse(segundaEvaluacion.disponible());
        assertEquals("subasta-1", segundaEvaluacion.subastaId());
    }

    @Test
    @DisplayName("sin identidad no se consulta ningun inventario")
    void exigeIdentidad() {
        assertThrows(IdentidadRequeridaException.class, () -> consulta.consultar(null, 0));
        assertThrows(IdentidadRequeridaException.class, () -> consulta.consultar("  ", 0));
    }

    @Test
    @DisplayName("una pagina negativa es una solicitud invalida")
    void rechazaPaginaNegativa() {
        assertThrows(IllegalArgumentException.class, () -> consulta.consultar("jugador-A", -1));
    }

    /** 1.7.0: veinte héroes y treinta objetos mezclados, en el orden en que llegaron. */
    private void conHeroesYObjetos() {
        Inventario inventario = Inventario.vacio("jugador-C");
        for (int i = 0; i < 50; i++) {
            boolean heroe = i % 5 < 2;
            inventario = inventario.agregar(new ElementoInventario(
                    "elemento-" + i, "producto-" + i,
                    heroe ? TipoElementoInventario.HEROE : TipoElementoInventario.ITEM,
                    (heroe ? "Heroe " : "Objeto ") + i));
        }
        repositorio.guardar(inventario);
    }

    @Test
    @DisplayName("1.7.0: la categoría HEROES pagina solo los héroes, de dieciséis en dieciséis")
    void paginaSoloHeroes() {
        conHeroesYObjetos();

        var primera = consulta.consultar("jugador-C", 0, CategoriaInventario.HEROES);
        var segunda = consulta.consultar("jugador-C", 1, CategoriaInventario.HEROES);

        assertEquals(20, primera.totalElementos());
        assertEquals(2, primera.totalPaginas());
        assertEquals(16, primera.elementos().size());
        assertEquals(4, segunda.elementos().size());
        assertTrue(segunda.ultima());
        assertTrue(primera.elementos().stream()
                .allMatch(e -> e.tipo() == TipoElementoInventario.HEROE));
    }

    @Test
    @DisplayName("1.7.0: la categoría OBJETOS pagina todo lo que no es héroe")
    void paginaSoloObjetos() {
        conHeroesYObjetos();

        var pagina = consulta.consultar("jugador-C", 1, CategoriaInventario.OBJETOS);

        assertEquals(30, pagina.totalElementos());
        assertEquals(2, pagina.totalPaginas());
        assertEquals(14, pagina.elementos().size());
        assertTrue(pagina.elementos().stream()
                .noneMatch(e -> e.tipo() == TipoElementoInventario.HEROE));
    }

    @Test
    @DisplayName("1.7.0: sin categoría se pagina el inventario entero, como antes")
    void sinCategoriaComoAntes() {
        conHeroesYObjetos();

        assertEquals(50, consulta.consultar("jugador-C", 0, null).totalElementos());
        assertEquals(50, consulta.consultar("jugador-C", 0).totalElementos());
    }

    @Test
    @DisplayName("1.7.0: cada página dice qué héroe lleva cada objeto, y nada de los que no están puestos")
    void paginaConPortadores() {
        Inventario inventario = Inventario.vacio("jugador-D")
                .agregar(new ElementoInventario("heroe-1", "p-h", TipoElementoInventario.HEROE, "Ignis"))
                .agregar(new ElementoInventario("arma-1", "p-a", TipoElementoInventario.ARMA, "Espada"))
                .agregar(new ElementoInventario("item-1", "p-i", TipoElementoInventario.ITEM, "Pluma"));
        repositorio.guardar(inventario.equipar("heroe-1", "arma-1"));

        var pagina = consulta.consultar("jugador-D", 0, CategoriaInventario.OBJETOS);

        assertEquals(new EquipadoEn("heroe-1", "Ignis"), pagina.equipadoEn("arma-1"));
        assertNull(pagina.equipadoEn("item-1"));
    }
}
