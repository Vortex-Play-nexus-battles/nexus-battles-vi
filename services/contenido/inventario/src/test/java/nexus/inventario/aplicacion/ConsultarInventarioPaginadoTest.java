package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
