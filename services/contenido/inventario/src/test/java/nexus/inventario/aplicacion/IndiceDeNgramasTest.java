package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IndiceDeNgramasTest {

    private final IndiceDeNgramas indice = IndiceDeNgramas.construir(Map.of(
            "p-amuleto", "Amuleto de Bruma · Ítem · Mejora la sanación multiplicando x2",
            "p-mago", "Mago Fuego · Héroe · Misiles de magma · Vulcano · +3 al ataque",
            "p-casco", "Corona de hielo · Armadura · Casco · +1 a la defensa, +1 de vida",
            "p-espada", "Espada de una mano · Arma · +1 al ataque, +1 % de crítico al ataque"));

    @Test
    @DisplayName("§7.1: cuatro caracteres bastan para encontrar un producto por un fragmento")
    void cuatroCaracteresBastan() {
        assertEquals(Set.of("p-amuleto"), indice.buscar("brum"));
        assertEquals(Set.of("p-mago"), indice.buscar("vulc"));
    }

    @Test
    @DisplayName("encuentra por cualquier parte del texto: descripción, efectos y acciones")
    void encuentraPorCualquierInformacion() {
        assertEquals(Set.of("p-amuleto"), indice.buscar("sanación"));
        assertEquals(Set.of("p-casco", "p-espada"), indice.buscar("+1 a"));
        assertEquals(Set.of("p-mago", "p-espada"), indice.buscar("al ataque"));
    }

    @Test
    @DisplayName("no distingue tildes, mayúsculas ni espacios repetidos")
    void normaliza() {
        assertEquals(Set.of("p-amuleto"), indice.buscar("  SANACION  "));
        assertEquals(Set.of("p-espada"), indice.buscar("CRÍTICO"));
        assertEquals(Set.of("p-espada"), indice.buscar("espada   de"));
    }

    @Test
    @DisplayName("los cuatrigramas no bastan: la subcadena entera tiene que estar")
    void confirmaLaSubcadena() {
        // «mago» y «magma» comparten cuatrigramas con otras palabras, pero
        // «mago de bruma» no aparece entero en ningún texto.
        assertTrue(indice.buscar("mago de bruma").isEmpty());
        assertTrue(indice.buscar("zzzz").isEmpty());
    }

    @Test
    @DisplayName("menos de cuatro caracteres no encuentra nada")
    void menosDeCuatroNoBusca() {
        assertTrue(indice.buscar("bru").isEmpty());
        assertTrue(indice.buscar(null).isEmpty());
        assertTrue(IndiceDeNgramas.vacio().buscar("bruma").isEmpty());
    }

    @Test
    @DisplayName("ignora ids nulos y textos demasiado cortos al construir")
    void ignoraEntradasInservibles() {
        Map<String, String> textos = new HashMap<>();
        textos.put(null, "Texto sin id");
        textos.put("p-corto", "abc");
        textos.put("p-ok", "Benditas");
        IndiceDeNgramas construido = IndiceDeNgramas.construir(textos);

        assertEquals(1, construido.tamanio());
        assertEquals(Set.of("p-ok"), construido.buscar("bendi"));
    }
}
