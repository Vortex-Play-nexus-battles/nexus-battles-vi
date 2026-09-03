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
    @DisplayName("renombrar un elemento conserva su identidad y referencia al catalogo")
    void renombrarElemento() {
        ElementoInventario original = new ElementoInventario(
                "elemento-1", "producto-1", TipoElementoInventario.ITEM, "Amuleto");
        Inventario inventario = Inventario.vacio("jugador-A").agregar(original);

        Inventario actualizado = inventario.renombrarElemento("elemento-1", "Amuleto de Bruma");

        ElementoInventario renombrado = actualizado.elemento("elemento-1");
        assertEquals("elemento-1", renombrado.id());
        assertEquals("producto-1", renombrado.productoId());
        assertEquals("Amuleto de Bruma", renombrado.nombrePropio());
        assertEquals("Amuleto", inventario.elemento("elemento-1").nombrePropio());
    }

    @Test
    @DisplayName("renombrar un elemento inexistente se rechaza")
    void renombrarElementoInexistente() {
        Inventario inventario = Inventario.vacio("jugador-A");

        assertThrows(ElementoNoEncontradoException.class,
                () -> inventario.renombrarElemento("elemento-inexistente", "Otro nombre"));
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

    @Test
    @DisplayName("equipa sobre un heroe propio un elemento que existe en el inventario")
    void equiparElementoExistente() {
        ElementoInventario heroe = new ElementoInventario(
                "heroe-1", "producto-heroe", TipoElementoInventario.HEROE, "Guerrero");
        ElementoInventario arma = new ElementoInventario(
                "arma-1", "producto-arma", TipoElementoInventario.ARMA, "Espada");
        Inventario inventario = Inventario.vacio("jugador-A").agregar(heroe).agregar(arma);

        Inventario actualizado = inventario.equipar("heroe-1", "arma-1");

        assertEquals(List.of("arma-1"), actualizado.equipamiento("heroe-1").armas());
        assertEquals(List.of(), inventario.equipamiento("heroe-1").armas());
    }

    @Test
    @DisplayName("solo una instancia de heroe puede recibir equipamiento")
    void destinoDebeSerHeroe() {
        ElementoInventario item = new ElementoInventario(
                "item-1", "producto-item", TipoElementoInventario.ITEM, "Pocion");
        Inventario inventario = Inventario.vacio("jugador-A").agregar(item);

        assertThrows(ElementoNoEquipableException.class,
                () -> inventario.equipar("item-1", "item-1"));
    }

    @Test
    @DisplayName("no se puede equipar una instancia que no pertenece al inventario")
    void elementoDebeExistir() {
        ElementoInventario heroe = new ElementoInventario(
                "heroe-1", "producto-heroe", TipoElementoInventario.HEROE, "Guerrero");
        Inventario inventario = Inventario.vacio("jugador-A").agregar(heroe);

        assertThrows(ElementoNoEncontradoException.class,
                () -> inventario.equipar("heroe-1", "arma-ajena"));
    }

    @Test
    @DisplayName("una misma instancia no se equipa simultaneamente en dos heroes")
    void elementoEnUnSoloHeroe() {
        ElementoInventario heroeA = new ElementoInventario(
                "heroe-A", "producto-heroe-A", TipoElementoInventario.HEROE, "Guerrero");
        ElementoInventario heroeB = new ElementoInventario(
                "heroe-B", "producto-heroe-B", TipoElementoInventario.HEROE, "Mago");
        ElementoInventario arma = new ElementoInventario(
                "arma-1", "producto-arma", TipoElementoInventario.ARMA, "Espada");
        Inventario inventario = Inventario.vacio("jugador-A")
                .agregar(heroeA).agregar(heroeB).agregar(arma)
                .equipar("heroe-A", "arma-1");

        assertThrows(ElementoYaEquipadoException.class,
                () -> inventario.equipar("heroe-B", "arma-1"));
    }

    private static Stream<TipoElementoInventario> tiposDeProducto() {
        return Stream.of(TipoElementoInventario.values());
    }
}
