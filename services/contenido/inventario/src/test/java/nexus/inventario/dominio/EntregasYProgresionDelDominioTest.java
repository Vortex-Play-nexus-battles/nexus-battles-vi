package nexus.inventario.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** B4 — las reglas nuevas del dominio: origen de lo entregado, nivel de los heroes y entregas. */
class EntregasYProgresionDelDominioTest {

    private static ElementoInventario heroe(Integer nivel, Double experiencia) {
        return new ElementoInventario("h-1", "guerrero", TipoElementoInventario.HEROE, "Aquiles",
                null, null, null, null, nivel, experiencia);
    }

    @Nested
    @DisplayName("la progresion de un heroe")
    class Progresion {

        @Test
        @DisplayName("un heroe sin nivel ni experiencia (anterior a B4) esta en nivel 1 con 0")
        void valoresIniciales() {
            ElementoInventario anterior = new ElementoInventario("h-1", "guerrero", TipoElementoInventario.HEROE, "A");

            assertEquals(ElementoInventario.NIVEL_INICIAL, anterior.nivel());
            assertEquals(0d, anterior.experiencia());
        }

        @Test
        @DisplayName("el nivel va de 1 a 8 y la experiencia no es negativa")
        void limites() {
            assertEquals(8, heroe(8, 10d).nivel());
            assertThrows(IllegalArgumentException.class, () -> heroe(0, 0d));
            assertThrows(IllegalArgumentException.class, () -> heroe(9, 0d));
            assertThrows(IllegalArgumentException.class, () -> heroe(2, -1d));
        }

        @Test
        @DisplayName("solo un heroe tiene nivel y experiencia")
        void soloHeroes() {
            assertThrows(IllegalArgumentException.class, () -> new ElementoInventario(
                    "a-1", "espada", TipoElementoInventario.ARMA, "Espada", null, null, null, null, 1, null));
            assertThrows(IllegalArgumentException.class, () -> new ElementoInventario(
                    "a-1", "espada", TipoElementoInventario.ARMA, "Espada", null, null, null, null, null, 0d));
            ElementoInventario arma = new ElementoInventario("a-1", "espada", TipoElementoInventario.ARMA, "Espada");
            assertNull(arma.nivel());
            assertNull(arma.experiencia());
        }

        @Test
        @DisplayName("renombrar y bloquear conservan nivel, experiencia, origen y referencia")
        void seConservan() {
            ElementoInventario original = new ElementoInventario("h-1", "guerrero", TipoElementoInventario.HEROE,
                    "Aquiles", null, null, OrigenDeEntrega.COMPRA, "orden-1", 5, 42.5);

            ElementoInventario renombrado = original.renombrar("Hector");
            ElementoInventario liberado = renombrado.bloquearEnSubasta("s-1").liberarBloqueoSubasta("s-1");

            for (ElementoInventario elemento : List.of(renombrado, liberado)) {
                assertEquals(5, elemento.nivel());
                assertEquals(42.5, elemento.experiencia());
                assertEquals(OrigenDeEntrega.COMPRA, elemento.origen());
                assertEquals("orden-1", elemento.referencia());
            }
        }
    }

    @Nested
    @DisplayName("un elemento entregado")
    class Entregado {

        @Test
        @DisplayName("lleva su canal y su referencia; sin canal no hay entrega")
        void canalObligatorio() {
            ElementoInventario entregado = ElementoInventario.entregado("e-1", "peto",
                    TipoElementoInventario.ARMADURA, "Peto", ParteArmadura.PECHO, OrigenDeEntrega.PREMIO_TORNEO, "t-1");

            assertEquals(OrigenDeEntrega.PREMIO_TORNEO, entregado.origen());
            assertEquals("t-1", entregado.referencia());
            assertTrue(entregado.disponible());
            assertThrows(NullPointerException.class, () -> ElementoInventario.entregado("e-1", "peto",
                    TipoElementoInventario.ARMADURA, "Peto", ParteArmadura.PECHO, null, "t-1"));
            assertThrows(IllegalArgumentException.class, () -> ElementoInventario.entregado("e-1", "peto",
                    TipoElementoInventario.ARMADURA, "Peto", ParteArmadura.PECHO, OrigenDeEntrega.COMPRA, " "));
        }

        @Test
        @DisplayName("la parte de una armadura se corrige con la del catalogo; otro tipo no tiene parte")
        void conParte() {
            ElementoInventario peto = new ElementoInventario("e-1", "peto", TipoElementoInventario.ARMADURA, "Peto",
                    ParteArmadura.CASCO);

            assertEquals(ParteArmadura.PECHO, peto.conParte(ParteArmadura.PECHO).parteArmadura());
            assertThrows(IllegalArgumentException.class, () -> new ElementoInventario(
                    "a-1", "espada", TipoElementoInventario.ARMA, "Espada").conParte(ParteArmadura.PECHO));
        }
    }

    @Nested
    @DisplayName("una entrega")
    class Entregas {

        private final List<ElementoInventario> recibidos = List.of(
                new ElementoInventario("e-1", "espada", TipoElementoInventario.ARMA, "Espada"),
                new ElementoInventario("e-2", "espada", TipoElementoInventario.ARMA, "Espada"));

        @Test
        @DisplayName("se aplica con todos sus elementos y queda anotada; aplicarla otra vez no cambia nada")
        void idempotente() {
            Inventario conEntrega = Inventario.vacio("jugador").recibirEntrega("entrega-1", recibidos);

            assertEquals(recibidos, conEntrega.elementos());
            assertTrue(conEntrega.recibio("entrega-1"));
            assertFalse(conEntrega.recibio("entrega-2"));
            assertSame(conEntrega, conEntrega.recibirEntrega("entrega-1", recibidos));
        }

        @Test
        @DisplayName("todo o nada: un elemento repetido rechaza la entrega entera")
        void todoONada() {
            Inventario conUno = Inventario.vacio("jugador")
                    .agregar(new ElementoInventario("e-2", "espada", TipoElementoInventario.ARMA, "Mia"));

            assertThrows(IllegalArgumentException.class, () -> conUno.recibirEntrega("entrega-1", recibidos));
            assertThrows(IllegalArgumentException.class, () -> conUno.recibirEntrega(" ", recibidos));
            assertEquals(1, conUno.elementos().size());
        }

        @Test
        @DisplayName("las demas operaciones conservan la version y las entregas anotadas")
        void seConservanVersionYEntregas() {
            Inventario guardado = new Inventario("inv-1", "jugador",
                    List.of(new ElementoInventario("h-1", "guerrero", TipoElementoInventario.HEROE, "Aquiles"),
                            new ElementoInventario("e-1", "espada", TipoElementoInventario.ARMA, "Espada")),
                    List.of(), List.of("entrega-0"), 4L);

            Inventario despues = guardado.renombrarElemento("e-1", "Tizona").equipar("h-1", "e-1")
                    .desequipar("h-1", "e-1").bloquearEnSubasta("e-1", "s-1").liberarBloqueoSubasta("e-1", "s-1");

            assertEquals(4L, despues.version());
            assertEquals(List.of("entrega-0"), despues.entregas());
        }

        @Test
        @DisplayName("una entrega pendiente pasa a completada con su momento; sus listas no se pueden modificar")
        void ciclo() {
            Entrega pendiente = Entrega.pendiente("entrega-1", "clave", "huella", "uid", OrigenDeEntrega.MISION,
                    "mision-3", List.of(new LineaDeEntrega("espada", 2)), recibidos, "misiones",
                    Instant.parse("2026-09-25T15:00:00Z"));

            Entrega completada = pendiente.completadaEn(Instant.parse("2026-09-25T15:00:02Z"));

            assertFalse(pendiente.completada());
            assertNull(pendiente.entregadaEn());
            assertTrue(completada.completada());
            assertEquals(Instant.parse("2026-09-25T15:00:02Z"), completada.entregadaEn());
            assertEquals(pendiente.elementos(), completada.elementos());
            assertThrows(UnsupportedOperationException.class, () -> completada.elementos().clear());
            assertThrows(NullPointerException.class, () -> new Entrega(null, "c", "h", "u", OrigenDeEntrega.MISION,
                    "r", List.of(), List.of(), EstadoEntrega.PENDIENTE, "s", null, null));
        }

        @Test
        @DisplayName("una linea nombra un producto y al menos una unidad")
        void lineas() {
            assertThrows(IllegalArgumentException.class, () -> new LineaDeEntrega(" ", 1));
            assertThrows(IllegalArgumentException.class, () -> new LineaDeEntrega(null, 1));
            assertThrows(IllegalArgumentException.class, () -> new LineaDeEntrega("espada", 0));
            assertEquals(3, new LineaDeEntrega("espada", 3).cantidad());
        }
    }
}
