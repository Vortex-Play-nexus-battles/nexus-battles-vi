package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas de SCRUM-318 - consulta paginada del inventario. */
class ConsultarInventarioPaginadoTest {

    @Test
    @DisplayName("la primera pagina contiene exactamente 16 de 17 elementos")
    void primeraPaginaDeDieciseis() {
        RepositorioDeInventarios repositorio = mock(RepositorioDeInventarios.class);
        when(repositorio.buscarPorPropietario("jugador-A"))
                .thenReturn(Optional.of(inventarioConElementos(17)));
        ConsultarInventarioPaginado consulta = new ConsultarInventarioPaginado(repositorio);

        PaginaInventario pagina = consulta.consultar("jugador-A", 0);

        assertEquals(16, pagina.elementos().size());
        assertEquals(0, pagina.numero());
        assertEquals(16, pagina.tamanio());
        assertEquals(17, pagina.totalElementos());
        assertEquals(2, pagina.totalPaginas());
        assertFalse(pagina.ultima());
    }

    @Test
    @DisplayName("la segunda pagina contiene el elemento restante sin repetir anteriores")
    void segundaPaginaConRestante() {
        RepositorioDeInventarios repositorio = mock(RepositorioDeInventarios.class);
        when(repositorio.buscarPorPropietario("jugador-A"))
                .thenReturn(Optional.of(inventarioConElementos(17)));
        ConsultarInventarioPaginado consulta = new ConsultarInventarioPaginado(repositorio);

        PaginaInventario pagina = consulta.consultar("jugador-A", 1);

        assertEquals(List.of("elemento-17"),
                pagina.elementos().stream().map(ElementoInventario::id).toList());
        assertTrue(pagina.ultima());
    }

    @Test
    @DisplayName("un jugador sin inventario recibe una pagina vacia en lugar de un error")
    void inventarioInexistenteEsPaginaVacia() {
        RepositorioDeInventarios repositorio = mock(RepositorioDeInventarios.class);
        when(repositorio.buscarPorPropietario("jugador-sin-productos"))
                .thenReturn(Optional.empty());
        ConsultarInventarioPaginado consulta = new ConsultarInventarioPaginado(repositorio);

        PaginaInventario pagina = consulta.consultar("jugador-sin-productos", 0);

        assertEquals(List.of(), pagina.elementos());
        assertEquals(0, pagina.totalElementos());
        assertEquals(0, pagina.totalPaginas());
        assertTrue(pagina.ultima());
    }

    @Test
    @DisplayName("la consulta rechaza paginas negativas e identificadores vacios")
    void parametrosInvalidos() {
        RepositorioDeInventarios repositorio = mock(RepositorioDeInventarios.class);
        ConsultarInventarioPaginado consulta = new ConsultarInventarioPaginado(repositorio);

        assertThrows(IllegalArgumentException.class,
                () -> consulta.consultar("jugador-A", -1));
        assertThrows(IllegalArgumentException.class,
                () -> consulta.consultar(" ", 0));
    }

    private Inventario inventarioConElementos(int cantidad) {
        List<ElementoInventario> elementos = IntStream.rangeClosed(1, cantidad)
                .mapToObj(numero -> new ElementoInventario(
                        "elemento-" + numero,
                        "producto-" + numero,
                        TipoElementoInventario.ITEM,
                        "Producto " + numero))
                .toList();
        return new Inventario("inventario-A", "jugador-A", elementos);
    }
}
