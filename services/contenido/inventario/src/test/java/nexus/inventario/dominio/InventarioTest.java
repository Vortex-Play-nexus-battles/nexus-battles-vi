package nexus.inventario.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class InventarioTest {

    @Test
    @DisplayName("un inventario nuevo pertenece a un jugador y comienza vacio")
    void inventarioNuevo() {
        Inventario inventario = Inventario.vacio("jugador-A");

        assertEquals("jugador-A", inventario.propietarioId());
        assertEquals(List.of(), inventario.elementos());
    }

    @ParameterizedTest
    @MethodSource("tiposDeProducto")
    @DisplayName("el inventario admite cada tipo de producto definido por el catalogo")
    void admiteTiposDeProducto(TipoElementoInventario tipo) {
        Inventario inventario = Inventario.vacio("jugador-A");
        ElementoInventario elemento = new ElementoInventario(
                "elemento-1", "producto-1", tipo, "Nombre propio");

        Inventario actualizado = inventario.agregar(elemento);

        assertEquals(List.of(elemento), actualizado.elementos());
        assertEquals(List.of(), inventario.elementos());
    }

    @Test
    @DisplayName("un elemento conserva una referencia al catalogo sin copiar sus atributos")
    void referenciaAlCatalogo() {
        ElementoInventario elemento = new ElementoInventario(
                "elemento-1", "producto-global-42", TipoElementoInventario.ITEM, "Amuleto de Niebla");

        assertEquals("producto-global-42", elemento.productoId());
        assertEquals("Amuleto de Niebla", elemento.nombrePropio());
    }

    @Test
    @DisplayName("no se aceptan dos instancias con el mismo identificador")
    void identificadorDeInstanciaUnico() {
        ElementoInventario original = new ElementoInventario(
                "elemento-1", "producto-1", TipoElementoInventario.HEROE, "Guerrero");
        ElementoInventario repetido = new ElementoInventario(
                "elemento-1", "producto-2", TipoElementoInventario.ITEM, "Pocion");
        Inventario inventario = Inventario.vacio("jugador-A").agregar(original);

        assertThrows(IllegalArgumentException.class, () -> inventario.agregar(repetido));
    }

    @Test
    @DisplayName("la coleccion interna no cambia desde fuera del agregado")
    void copiaDefensiva() {
        List<ElementoInventario> elementos = new ArrayList<>();
        Inventario inventario = new Inventario("inventario-1", "jugador-A", elementos);

        elementos.add(new ElementoInventario(
                "elemento-1", "producto-1", TipoElementoInventario.ITEM, "Pocion"));

        assertEquals(List.of(), inventario.elementos());
        assertThrows(UnsupportedOperationException.class, () -> inventario.elementos().clear());
    }

    private static Stream<TipoElementoInventario> tiposDeProducto() {
        return Stream.of(TipoElementoInventario.values());
    }
}
